package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgendamentoServiceTest {
    private static final LocalDate AMANHA = LocalDate.now().plusDays(1);

    private final TipoAtendimentoRepository tipoRepository = mock(TipoAtendimentoRepository.class);
    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final GestanteService gestanteService = mock(GestanteService.class);
    private final AgendamentoService service = new AgendamentoService(tipoRepository, agendamentoRepository,
            medicoRepository, gestanteService);

    private final Medico medico = new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false);
    private final Gestante gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash",
            medico, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());
    private final TipoAtendimento hemograma =
            new TipoAtendimento(3L, "Hemograma completo", CategoriaAtendimento.EXAME, 25, true);

    @BeforeEach
    void setUp() {
        when(gestanteService.gestanteDoMedico(7L, 1L)).thenReturn(gestante);
        when(medicoRepository.getReferenceById(7L)).thenReturn(medico);
        when(agendamentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static AgendamentoRequest dados(Long tipo) {
        return new AgendamentoRequest(1L, tipo, AMANHA, "Jejum de 8h");
    }

    private static TipoAtendimento tipo(Long id, String nome, CategoriaAtendimento categoria, int pontos) {
        return new TipoAtendimento(id, nome, categoria, pontos, true);
    }

    // O id só existe depois de persistir; a ordenação usa o id como desempate, então os testes precisam dele
    private Agendamento agendamento(long id, StatusAgendamento status, LocalDate dataPrevista) {
        Agendamento agendamento = new Agendamento(gestante, hemograma, medico, dataPrevista, null);
        if (status != StatusAgendamento.AGENDADO) {
            agendamento.registrarDesfecho(status, medico);
        }
        try {
            Field campo = Agendamento.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(agendamento, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return agendamento;
    }

    @Test
    void listaSoOsTiposAtivosDaCategoria() {
        when(tipoRepository.findByCategoriaAndAtivoTrueOrderByNomeAsc(CategoriaAtendimento.EXAME)).thenReturn(
                List.of(tipo(1L, "Hemograma completo", CategoriaAtendimento.EXAME, 25),
                        tipo(2L, "Urina tipo I (EAS)", CategoriaAtendimento.EXAME, 20)));

        var resposta = service.listarTiposAtivos(CategoriaAtendimento.EXAME);

        assertEquals(2, resposta.size());
        assertEquals("Hemograma completo", resposta.getFirst().nome());
        assertEquals(CategoriaAtendimento.EXAME, resposta.getFirst().categoria());
        assertEquals(25, resposta.getFirst().pontosBase());
        verify(tipoRepository).findByCategoriaAndAtivoTrueOrderByNomeAsc(CategoriaAtendimento.EXAME);
        verify(tipoRepository, never()).findByAtivoTrueOrderByNomeAsc();
    }

    @Test
    void semCategoriaListaTodosOsAtivos() {
        when(tipoRepository.findByAtivoTrueOrderByNomeAsc()).thenReturn(
                List.of(tipo(1L, "Consulta puerperal", CategoriaAtendimento.CONSULTA, 50),
                        tipo(2L, "Hemograma completo", CategoriaAtendimento.EXAME, 25)));

        var resposta = service.listarTiposAtivos(null);

        assertEquals(2, resposta.size());
        verify(tipoRepository).findByAtivoTrueOrderByNomeAsc();
        verify(tipoRepository, never()).findByCategoriaAndAtivoTrueOrderByNomeAsc(any());
    }

    @Test
    void cadastraComStatusAgendadoEMedicoDoToken() {
        when(tipoRepository.findById(3L)).thenReturn(Optional.of(hemograma));

        var resposta = service.agendar(dados(3L), 7L);

        var salvo = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository).save(salvo.capture());
        assertEquals(StatusAgendamento.AGENDADO, salvo.getValue().getStatus());
        assertSame(medico, salvo.getValue().getAtribuidoPor());
        assertSame(gestante, salvo.getValue().getGestante());
        assertSame(hemograma, salvo.getValue().getTipo());
        assertEquals("Hemograma completo", resposta.tipo());
        assertEquals(CategoriaAtendimento.EXAME, resposta.categoria());
        assertEquals(AMANHA, resposta.dataPrevista());
        assertEquals("Jejum de 8h", resposta.observacao());
        assertNull(resposta.dataConfirmacao());
    }

    // Um tipo desativado some do catálogo, mas o id ainda existe: o cadastro precisa recusá-lo
    @Test
    void recusaTipoInativo() {
        when(tipoRepository.findById(4L)).thenReturn(Optional.of(
                new TipoAtendimento(4L, "Exame descontinuado", CategoriaAtendimento.EXAME, 25, false)));

        var erro = assertThrows(DadosInvalidosException.class, () -> service.agendar(dados(4L), 7L));

        assertEquals("Tipo de atendimento inválido", erro.getMessage());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void recusaTipoInexistente() {
        when(tipoRepository.findById(99L)).thenReturn(Optional.empty());

        var erro = assertThrows(DadosInvalidosException.class, () -> service.agendar(dados(99L), 7L));

        assertEquals("Tipo de atendimento inválido", erro.getMessage());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void naoCadastraParaGestanteForaDoAlcanceDoMedico() {
        when(gestanteService.gestanteDoMedico(8L, 1L))
                .thenThrow(new NaoEncontradoException("Gestante não encontrada"));

        var erro = assertThrows(NaoEncontradoException.class, () -> service.agendar(dados(3L), 8L));

        assertEquals("Gestante não encontrada", erro.getMessage());
        verify(agendamentoRepository, never()).save(any());
        // A gestante é conferida antes do tipo, como no diagrama: 404 vem antes de 400
        verify(tipoRepository, never()).findById(any());
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
        when(tipoRepository.findById(3L)).thenReturn(Optional.of(hemograma));

        service.agendar(dados(3L), 7L);

        assertEquals(0, gestante.getSaldoPontos());
    }
}
