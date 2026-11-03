package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.request.ItemAgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.AtendimentoDoDiaResponse;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.cozyshelf.previna.domain.Campos;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgendamentoServiceTest {
    private static final LocalDate AMANHA = LocalDate.now().plusDays(1);

    private final TipoAtendimentoService tipoService = mock(TipoAtendimentoService.class);
    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final GestanteService gestanteService = mock(GestanteService.class);
    private final PontuacaoService pontuacaoService = mock(PontuacaoService.class);
    private final AgendamentoService service = new AgendamentoService(tipoService, agendamentoRepository,
            medicoRepository, gestanteService, pontuacaoService);

    private final Medico medico = Campos.definir(
            new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false), "id", 7L);
    private final Gestante gestante = Campos.definir(new Gestante("Maria", "12345678909", null, "12345678909",
            "hash", medico, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()), "id", 1L);
    private final TipoAtendimento hemograma = Campos.definir(
            new TipoAtendimento(null, "Hemograma completo", CategoriaAtendimento.EXAME, 25, true), "id", 3L);
    private final TipoAtendimento consulta = Campos.definir(
            new TipoAtendimento(7L, "Consulta puerperal", CategoriaAtendimento.CONSULTA, 50, true), "id", 4L);

    @BeforeEach
    void setUp() {
        when(gestanteService.gestanteDoMedico(7L, 1L)).thenReturn(gestante);
        when(medicoRepository.getReferenceById(7L)).thenReturn(medico);
        when(agendamentoRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(gestanteService.podeAcessar(7L, gestante)).thenReturn(true);
    }

    private static AgendamentoRequest dados(Long... tipos) {
        return new AgendamentoRequest(1L, java.util.Arrays.stream(tipos)
                .map(tipo -> new ItemAgendamentoRequest(tipo, AMANHA, "Jejum de 8h"))
                .toList());
    }

    // O id só existe depois de persistir; a ordenação usa o id como desempate, então os testes precisam dele
    private Agendamento agendamento(long id, StatusAgendamento status, LocalDate dataPrevista) {
        Agendamento agendamento = new Agendamento(gestante, hemograma, medico, dataPrevista, null);
        if (status != StatusAgendamento.AGENDADO) {
            agendamento.registrarDesfecho(status, medico);
        }
        return Campos.definir(agendamento, "id", id);
    }

    @Test
    void cadastraComStatusAgendadoEMedicoDoToken() {
        when(tipoService.paraAgendamento(7L, 3L)).thenReturn(hemograma);

        var resposta = service.agendar(dados(3L), 7L).getFirst();

        var salvos = ArgumentCaptor.forClass(List.class);
        verify(agendamentoRepository).saveAll(salvos.capture());
        var salvo = (Agendamento) salvos.getValue().getFirst();
        assertEquals(StatusAgendamento.AGENDADO, salvo.getStatus());
        assertSame(medico, salvo.getAtribuidoPor());
        assertSame(gestante, salvo.getGestante());
        assertSame(hemograma, salvo.getTipo());
        assertEquals("Hemograma completo", resposta.tipo());
        assertEquals(CategoriaAtendimento.EXAME, resposta.categoria());
        assertEquals(AMANHA, resposta.dataPrevista());
        assertEquals("Jejum de 8h", resposta.observacao());
        assertNull(resposta.dataConfirmacao());
    }

    // Opção escolhida: um cadastro com vários itens vira um agendamento independente por item
    @Test
    void cadastraUmAgendamentoPorItemDoFormulario() {
        when(tipoService.paraAgendamento(7L, 3L)).thenReturn(hemograma);
        when(tipoService.paraAgendamento(7L, 4L)).thenReturn(consulta);

        var resposta = service.agendar(dados(3L, 4L), 7L);

        var salvos = ArgumentCaptor.forClass(List.class);
        verify(agendamentoRepository).saveAll(salvos.capture());
        assertEquals(2, salvos.getValue().size());
        assertEquals(List.of("Hemograma completo", "Consulta puerperal"),
                resposta.stream().map(AgendamentoResponse::tipo).toList());
        // Cada item nasce pendente e com o próprio desfecho por resolver
        assertEquals(List.of(StatusAgendamento.AGENDADO, StatusAgendamento.AGENDADO),
                resposta.stream().map(AgendamentoResponse::status).toList());
    }

    // Tudo na mesma transação: um tipo recusado no meio da lista não deixa metade do cadastro gravada
    @Test
    void umItemInvalidoNaListaNaoGravaNenhumOutro() {
        when(tipoService.paraAgendamento(7L, 3L)).thenReturn(hemograma);
        when(tipoService.paraAgendamento(7L, 99L))
                .thenThrow(new DadosInvalidosException("Tipo de atendimento inválido"));

        var erro = assertThrows(DadosInvalidosException.class, () -> service.agendar(dados(3L, 99L), 7L));

        assertEquals("Tipo de atendimento inválido", erro.getMessage());
        verify(agendamentoRepository, never()).saveAll(any());
    }

    @Test
    void naoCadastraParaGestanteForaDoAlcanceDoMedico() {
        when(gestanteService.gestanteDoMedico(8L, 1L))
                .thenThrow(new NaoEncontradoException("Gestante não encontrada"));

        var erro = assertThrows(NaoEncontradoException.class, () -> service.agendar(dados(3L), 8L));

        assertEquals("Gestante não encontrada", erro.getMessage());
        verify(agendamentoRepository, never()).saveAll(any());
        // A gestante é conferida antes dos tipos, como no diagrama: 404 vem antes de 400
        verify(tipoService, never()).paraAgendamento(any(), any());
    }

    // Tela inicial: só o que está pendente para hoje, na ordem de cadastro (o id), com o nome da gestante
    @Test
    void listaOsAtendimentosPendentesDeHoje() {
        when(medicoRepository.findById(7L)).thenReturn(Optional.of(medico));
        when(agendamentoRepository.findByStatusAndDataPrevistaAndGestanteMedicoIdOrderByIdAsc(
                StatusAgendamento.AGENDADO, LocalDate.now(), 7L))
                .thenReturn(List.of(agendamento(2L, StatusAgendamento.AGENDADO, LocalDate.now()),
                        agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now())));

        var resposta = service.listarDeHoje(7L);

        assertEquals(List.of(2L, 5L), resposta.stream().map(AtendimentoDoDiaResponse::id).toList());
        assertEquals(1L, resposta.getFirst().gestante());
        assertEquals("Maria", resposta.getFirst().nomeGestante());
        assertEquals("Hemograma completo", resposta.getFirst().tipo());
        // Sem a permissão do administrador, o médico não vê a agenda das gestantes dos outros
        verify(agendamentoRepository, never()).findByStatusAndDataPrevistaOrderByIdAsc(any(), any());
    }

    // Com a permissão do administrador, a tela inicial mostra a agenda de todas as gestantes
    @Test
    void medicoQueVeTodasAsGestantesVeAAgendaInteiraDoDia() {
        var comPermissao = Campos.definir(
                new Medico("Dr. Bruno", "22222222222", null, "CRM-SP 2", "bruno", "hash", true), "id", 9L);
        when(medicoRepository.findById(9L)).thenReturn(Optional.of(comPermissao));
        when(agendamentoRepository.findByStatusAndDataPrevistaOrderByIdAsc(
                StatusAgendamento.AGENDADO, LocalDate.now()))
                .thenReturn(List.of(agendamento(2L, StatusAgendamento.AGENDADO, LocalDate.now())));

        assertEquals(1, service.listarDeHoje(9L).size());
        verify(agendamentoRepository, never())
                .findByStatusAndDataPrevistaAndGestanteMedicoIdOrderByIdAsc(any(), any(), any());
    }

    @Test
    void diaSemNenhumAtendimentoDevolveListaVazia() {
        when(medicoRepository.findById(7L)).thenReturn(Optional.of(medico));
        when(agendamentoRepository.findByStatusAndDataPrevistaAndGestanteMedicoIdOrderByIdAsc(
                StatusAgendamento.AGENDADO, LocalDate.now(), 7L)).thenReturn(List.of());

        assertEquals(List.of(), service.listarDeHoje(7L));
    }

    // Pendentes primeiro, para o médico avaliar; depois os resolvidos, pela data prevista
    @Test
    void listaPendentesPrimeiroDepoisPorDataPrevista() {
        when(agendamentoRepository.findByGestanteId(1L)).thenReturn(List.of(
                agendamento(1L, StatusAgendamento.REALIZADO, LocalDate.of(2026, 1, 10)),
                agendamento(2L, StatusAgendamento.AGENDADO, LocalDate.of(2026, 1, 20)),
                agendamento(3L, StatusAgendamento.NAO_COMPARECEU, LocalDate.of(2026, 1, 5)),
                agendamento(4L, StatusAgendamento.AGENDADO, LocalDate.of(2026, 1, 15))));

        var resposta = service.listarDaGestante(7L, 1L);

        assertEquals(List.of(StatusAgendamento.AGENDADO, StatusAgendamento.AGENDADO,
                        StatusAgendamento.REALIZADO, StatusAgendamento.NAO_COMPARECEU),
                resposta.stream().map(AgendamentoResponse::status).toList());
        assertEquals(List.of(4L, 2L, 1L, 3L), resposta.stream().map(AgendamentoResponse::id).toList());
    }

    @Test
    void desempataPeloIdQuandoStatusEDataSaoIguais() {
        when(agendamentoRepository.findByGestanteId(1L)).thenReturn(List.of(
                agendamento(9L, StatusAgendamento.AGENDADO, LocalDate.of(2026, 1, 20)),
                agendamento(2L, StatusAgendamento.AGENDADO, LocalDate.of(2026, 1, 20))));

        var resposta = service.listarDaGestante(7L, 1L);

        assertEquals(List.of(2L, 9L), resposta.stream().map(AgendamentoResponse::id).toList());
    }

    @Test
    void gestanteSemAgendamentosDevolveListaVazia() {
        when(agendamentoRepository.findByGestanteId(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.listarDaGestante(7L, 1L));
    }

    @Test
    void naoListaAgendamentosDeGestanteForaDoAlcanceDoMedico() {
        when(gestanteService.gestanteDoMedico(8L, 1L))
                .thenThrow(new NaoEncontradoException("Gestante não encontrada"));

        assertThrows(NaoEncontradoException.class, () -> service.listarDaGestante(8L, 1L));
        verify(agendamentoRepository, never()).findByGestanteId(any());
    }

    // O cadastro não credita nada: os pontos só saem na confirmação
    @Test
    void naoCreditaPontosNoCadastro() {
        when(tipoService.paraAgendamento(7L, 3L)).thenReturn(hemograma);

        service.agendar(dados(3L), 7L);

        assertEquals(0, gestante.getSaldoPontos());
    }

    @Test
    void confirmaCreditaPontosEDevolveOSaldo() {
        var pendente = agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now());
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(pendente));
        when(pontuacaoService.creditarPorAgendamento(pendente)).thenAnswer(i -> {
            gestante.creditarPontos(25);
            return 25;
        });

        var resposta = service.confirmar(5L, 7L);

        assertEquals(StatusAgendamento.REALIZADO, pendente.getStatus());
        assertNotNull(pendente.getDataConfirmacao());
        assertSame(medico, pendente.getConfirmadoPor());
        assertEquals(25, resposta.pontosCreditados());
        assertEquals(25, resposta.saldoPontos());
        assertEquals(StatusAgendamento.REALIZADO, resposta.agendamento().status());
    }

    // O médico só registra o desfecho a partir do dia marcado; antes disso não há o que confirmar
    @Test
    void naoResolveAgendamentoAntesDaDataPrevista() {
        var futuro = agendamento(5L, StatusAgendamento.AGENDADO, AMANHA);
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(futuro));

        var erro = assertThrows(ConflitoException.class, () -> service.confirmar(5L, 7L));
        assertThrows(ConflitoException.class, () -> service.registrarNaoComparecimento(5L, 7L));

        assertEquals("Este agendamento só pode ser resolvido a partir da data prevista", erro.getMessage());
        assertEquals(StatusAgendamento.AGENDADO, futuro.getStatus());
        verify(pontuacaoService, never()).creditarPorAgendamento(any());
    }

    // No próprio dia já vale: a comparação é só de data, sem horário
    @Test
    void resolveAgendamentoNoProprioDiaPrevisto() {
        var hoje = agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now());
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(hoje));

        service.registrarNaoComparecimento(5L, 7L);

        assertEquals(StatusAgendamento.NAO_COMPARECEU, hoje.getStatus());
    }

    @Test
    void confirmarAgendamentoJaRealizadoDaConflito() {
        when(agendamentoRepository.findParaResolver(5L))
                .thenReturn(Optional.of(agendamento(5L, StatusAgendamento.REALIZADO, LocalDate.now())));

        var erro = assertThrows(ConflitoException.class, () -> service.confirmar(5L, 7L));

        assertEquals("Este agendamento já foi resolvido", erro.getMessage());
        verify(pontuacaoService, never()).creditarPorAgendamento(any());
    }

    @Test
    void confirmarAgendamentoNaoComparecidoDaConflito() {
        when(agendamentoRepository.findParaResolver(5L))
                .thenReturn(Optional.of(agendamento(5L, StatusAgendamento.NAO_COMPARECEU, LocalDate.now())));

        assertThrows(ConflitoException.class, () -> service.confirmar(5L, 7L));
        verify(pontuacaoService, never()).creditarPorAgendamento(any());
    }

    // Agendamento de gestante fora do alcance responde como inexistente, e com a mensagem do agendamento:
    // a do 404 da gestante revelaria qual dos dois existe
    @Test
    void confirmarAgendamentoDeGestanteForaDoAlcanceDaNaoEncontrado() {
        var pendente = agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now());
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(pendente));
        when(gestanteService.podeAcessar(8L, gestante)).thenReturn(false);

        var erro = assertThrows(NaoEncontradoException.class, () -> service.confirmar(5L, 8L));

        assertEquals("Agendamento não encontrado", erro.getMessage());
        assertEquals(StatusAgendamento.AGENDADO, pendente.getStatus());
        verify(pontuacaoService, never()).creditarPorAgendamento(any());
    }

    @Test
    void confirmarAgendamentoInexistenteDaNaoEncontrado() {
        when(agendamentoRepository.findParaResolver(99L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.confirmar(99L, 7L));

        assertEquals("Agendamento não encontrado", erro.getMessage());
    }

    @Test
    void naoComparecimentoNaoCreditaPontos() {
        var pendente = agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now());
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(pendente));

        var resposta = service.registrarNaoComparecimento(5L, 7L);

        assertEquals(StatusAgendamento.NAO_COMPARECEU, pendente.getStatus());
        assertNotNull(pendente.getDataConfirmacao());
        assertSame(medico, pendente.getConfirmadoPor());
        assertEquals(StatusAgendamento.NAO_COMPARECEU, resposta.status());
        assertEquals(0, gestante.getSaldoPontos());
        verify(pontuacaoService, never()).creditarPorAgendamento(any());
    }

    @Test
    void naoComparecimentoEmItemJaResolvidoDaConflito() {
        when(agendamentoRepository.findParaResolver(5L))
                .thenReturn(Optional.of(agendamento(5L, StatusAgendamento.REALIZADO, LocalDate.now())));

        assertThrows(ConflitoException.class, () -> service.registrarNaoComparecimento(5L, 7L));
    }

    // Duas confirmações ao mesmo tempo leem AGENDADO; o UNIQUE de agendamento_id derruba a segunda,
    // e a transação inteira volta atrás — o saldo nunca é creditado duas vezes
    @Test
    void segundaConfirmacaoSimultaneaNaoCreditaDeNovo() {
        var pendente = agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now());
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(pendente));
        when(pontuacaoService.creditarPorAgendamento(pendente))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThrows(DataIntegrityViolationException.class, () -> service.confirmar(5L, 7L));

        assertEquals(0, gestante.getSaldoPontos());
    }

    // No app o id vem do token, nunca do caminho: a gestante não alcança a lista de outra
    @Test
    void gestanteVeApenasOsProprios() {
        when(agendamentoRepository.findByGestanteId(1L)).thenReturn(List.of(
                agendamento(1L, StatusAgendamento.REALIZADO, LocalDate.of(2026, 1, 10)),
                agendamento(2L, StatusAgendamento.AGENDADO, LocalDate.of(2026, 1, 20))));

        var resposta = service.listarDaGestanteAutenticada(1L);

        assertEquals(List.of(2L, 1L), resposta.stream().map(AgendamentoResponse::id).toList());
        verify(agendamentoRepository).findByGestanteId(1L);
        verifyNoInteractions(gestanteService);
    }

    @Test
    void gestanteSemAgendamentosDevolveListaVaziaNoApp() {
        when(agendamentoRepository.findByGestanteId(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.listarDaGestanteAutenticada(1L));
    }

    // Confirmar e registrar não comparecimento ao mesmo tempo liam o mesmo AGENDADO e um sobrescrevia o outro,
    // deixando um NAO_COMPARECEU com pontos creditados. O desfecho passa a travar a linha antes de decidir.
    @Test
    void resolverTravaALinhaAntesDeDecidirODesfecho() {
        var pendente = agendamento(5L, StatusAgendamento.AGENDADO, LocalDate.now());
        when(agendamentoRepository.findParaResolver(5L)).thenReturn(Optional.of(pendente));

        service.registrarNaoComparecimento(5L, 7L);

        assertEquals(StatusAgendamento.NAO_COMPARECEU, pendente.getStatus());
        verify(agendamentoRepository).findParaResolver(5L);
        // A busca sem trava não decide desfecho: ela não bloqueia a transação concorrente
        verify(agendamentoRepository, never()).findById(5L);
    }
}
