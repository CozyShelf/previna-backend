package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class PromocaoServiceTest {
    private static final LocalDate HOJE = LocalDate.now();

    private final PromocaoRepository promocaoRepository = mock(PromocaoRepository.class);
    private final CupomResgatadoRepository resgatadoRepository = mock(CupomResgatadoRepository.class);
    private final EmpresaParceiraRepository empresaRepository = mock(EmpresaParceiraRepository.class);
    private final PontuacaoService pontuacaoService = mock(PontuacaoService.class);
    private final PromocaoService service = new PromocaoService(promocaoRepository, resgatadoRepository,
            empresaRepository, pontuacaoService, new GeradorCodigoResgate(resgatadoRepository));

    private static PromocaoRequest dados(LocalDate inicio, LocalDate fim) {
        return new PromocaoRequest("Semana do bebê", null, new BigDecimal("15"), inicio, fim, Categoria.FRALDAS);
    }

    @Test
    void cadastraParaACategoriaNaEmpresaDoToken() {
        when(promocaoRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var resposta = service.cadastrar(1L, dados(HOJE, HOJE.plusDays(7)));

        var salva = ArgumentCaptor.forClass(Promocao.class);
        verify(promocaoRepository).saveAndFlush(salva.capture());
        assertEquals(Categoria.FRALDAS, salva.getValue().getCategoria());
        assertEquals(Categoria.FRALDAS, resposta.categoria());
        assertTrue(resposta.ativo());
    }

    @Test
    void aceitaPromocaoDeUmDiaSo() {
        when(promocaoRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(HOJE, service.cadastrar(1L, dados(HOJE, HOJE)).dataFim());
    }

    @Test
    void atualizaOsDadosDaPromocaoDaEmpresa() {
        var promocao = new Promocao(1L, "Antiga", null, BigDecimal.TEN, HOJE, HOJE, Categoria.ROUPAS);
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 1L)).thenReturn(Optional.of(promocao));

        var resposta = service.atualizar(1L, 5L, dados(HOJE, HOJE.plusDays(7)));

        assertEquals("Semana do bebê", resposta.titulo());
        assertEquals(Categoria.FRALDAS, resposta.categoria());
        assertEquals(HOJE.plusDays(7), promocao.getDataFim());
    }

    @Test
    void naoAtualizaComFimAntesDoInicio() {
        var promocao = new Promocao(1L, "Antiga", null, BigDecimal.TEN, HOJE, HOJE, Categoria.ROUPAS);
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 1L)).thenReturn(Optional.of(promocao));

        assertThrows(DadosInvalidosException.class,
                () -> service.atualizar(1L, 5L, dados(HOJE.plusDays(7), HOJE.plusDays(6))));
        assertEquals("Antiga", promocao.getTitulo());
    }

    @Test
    void naoAtualizaNemMostraPromocaoDeOutraEmpresa() {
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.atualizar(2L, 5L, dados(HOJE, HOJE)));
        assertThrows(NaoEncontradoException.class, () -> service.buscar(2L, 5L));
        assertThrows(NaoEncontradoException.class, () -> service.alterarAtivo(2L, 5L, false));
    }

    @Test
    void desativaEReativaAPromocaoDaEmpresa() {
        var promocao = new Promocao(1L, "Semana do bebê", null, BigDecimal.TEN, HOJE, HOJE, Categoria.ROUPAS);
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 1L)).thenReturn(Optional.of(promocao));

        assertFalse(service.alterarAtivo(1L, 5L, false).ativo());
        assertFalse(promocao.isAtivo());
        assertTrue(service.alterarAtivo(1L, 5L, true).ativo());
        assertTrue(promocao.isAtivo());
    }

    @Test
    void atualizarNaoMudaASituacao() {
        var promocao = new Promocao(1L, "Antiga", null, BigDecimal.TEN, HOJE, HOJE, Categoria.ROUPAS);
        promocao.alterarAtivo(false);
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 1L)).thenReturn(Optional.of(promocao));

        assertFalse(service.atualizar(1L, 5L, dados(HOJE, HOJE.plusDays(7))).ativo());
    }

    @Test
    void recusaFimAntesDoInicio() {
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(1L, dados(HOJE.plusDays(7), HOJE.plusDays(6))));
        verify(promocaoRepository, never()).saveAndFlush(any());
    }

    // ---- Sobreposição (exclusão promocao_sem_sobreposicao, V23) ----

    // A violação como o banco a devolve: o nome da restrição entre aspas na mensagem
    private static DataIntegrityViolationException violacao(String restricao) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: conflicting key value violates exclusion constraint \"" + restricao + "\""));
    }

    private static final String SOBREPOSTA = "Já existe promoção ativa dessa categoria nesse período";

    @Test
    void cadastroSobrepostoResponde409ComAMensagem() {
        when(promocaoRepository.saveAndFlush(any())).thenThrow(violacao("promocao_sem_sobreposicao"));

        var erro = assertThrows(ConflitoException.class, () -> service.cadastrar(1L, dados(HOJE, HOJE)));
        assertEquals(SOBREPOSTA, erro.getMessage());
    }

    @Test
    void edicaoEReativacaoSobrepostasRespondem409() {
        var promocao = new Promocao(1L, "Antiga", null, BigDecimal.TEN, HOJE, HOJE, Categoria.ROUPAS);
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 1L)).thenReturn(Optional.of(promocao));
        doThrow(violacao("promocao_sem_sobreposicao")).when(promocaoRepository).flush();

        assertEquals(SOBREPOSTA, assertThrows(ConflitoException.class,
                () -> service.atualizar(1L, 5L, dados(HOJE, HOJE.plusDays(7)))).getMessage());
        assertEquals(SOBREPOSTA, assertThrows(ConflitoException.class,
                () -> service.alterarAtivo(1L, 5L, true)).getMessage());
    }

    // Cadastrar, editar e ativar entram na fila da empresa antes de gravar; desativar nunca conflita e não entra
    @Test
    void soQuemPodeSobreporEntraNaFilaDaEmpresa() {
        var promocao = new Promocao(1L, "Antiga", null, BigDecimal.TEN, HOJE, HOJE, Categoria.ROUPAS);
        when(promocaoRepository.findByIdAndEmpresaIdParaAtualizar(5L, 1L)).thenReturn(Optional.of(promocao));
        when(promocaoRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.alterarAtivo(1L, 5L, false);
        verify(empresaRepository, never()).findByIdParaAtualizar(any());

        service.cadastrar(1L, dados(HOJE, HOJE));
        service.atualizar(1L, 5L, dados(HOJE, HOJE));
        service.alterarAtivo(1L, 5L, true);
        verify(empresaRepository, times(3)).findByIdParaAtualizar(1L);
    }

    @Test
    void outraViolacaoSegueParaOTratamentoGenerico() {
        var outra = violacao("promocao_empresa_id_fkey");
        when(promocaoRepository.saveAndFlush(any())).thenThrow(outra);

        assertSame(outra, assertThrows(DataIntegrityViolationException.class,
                () -> service.cadastrar(1L, dados(HOJE, HOJE))));
    }

    // ---- Resgate pela gestante ----

    private final EmpresaParceira farmacia = Campos.definir(
            new EmpresaParceira("Farmácia Vida", "12345678000190", null, null, null, "vida", "hash"), "id", 1L);

    private Gestante gestanteComSaldo(int saldo) {
        var medico = new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false);
        var gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());
        return Campos.definir(Campos.definir(gestante, "id", 7L), "saldoPontos", saldo);
    }

    private Promocao promocaoDe(LocalDate inicio, LocalDate fim) {
        var promocao = Campos.definir(new Promocao(1L, "Semana do bebê", null, new BigDecimal("15.00"), inicio, fim,
                Categoria.FRALDAS), "id", 5L);
        when(promocaoRepository.findById(5L)).thenReturn(Optional.of(promocao));
        return promocao;
    }

    private Gestante gestanteTravada(int saldo) {
        var gestante = gestanteComSaldo(saldo);
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestante);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(farmacia));
        when(resgatadoRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        return gestante;
    }

    @Test
    void resgataSemPontosComCodigoEValidadeNoFimDaPromocao() {
        var promocao = promocaoDe(HOJE.minusDays(2), HOJE.plusDays(5));
        var gestante = gestanteTravada(80);

        var resgate = service.resgatar(5L, 7L, null);

        assertFalse(resgate.repetido());
        var resposta = resgate.cupom();
        assertEquals(TipoResgate.PROMOCAO, resposta.tipo());
        assertTrue(resposta.codigo().matches("[A-HJ-KMNP-Z2-9]{8}"));
        assertEquals("15% de desconto em Fraldas", resposta.descricao());
        assertEquals("Farmácia Vida", resposta.empresa());
        assertEquals(HOJE.plusDays(5), resposta.dataValidade());
        assertEquals(StatusCupomResgatado.ATIVO, resposta.status());
        assertEquals(0, resposta.pontosGastos());
        assertEquals(80, resposta.saldo());

        var salvo = ArgumentCaptor.forClass(CupomResgatado.class);
        verify(resgatadoRepository).saveAndFlush(salvo.capture());
        var copia = salvo.getValue();
        assertSame(promocao, copia.getPromocao());
        assertSame(gestante, copia.getGestante());
        assertNull(copia.getCupom());
        assertNull(copia.getProdutoId());
        assertNull(copia.getProdutoNome());
        assertNull(copia.getPontosGastos());
        assertEquals("Semana do bebê", copia.getTitulo());
        assertEquals(Categoria.FRALDAS, copia.getCategoria());
        // Sem débito e sem transação de pontos
        verify(pontuacaoService, never()).debitarPontos(any(), anyInt(), any());
    }

    @Test
    void resgateValeNoPrimeiroENoUltimoDia() {
        promocaoDe(HOJE, HOJE);
        gestanteTravada(0);

        assertEquals(HOJE, service.resgatar(5L, 7L, null).cupom().dataValidade());
    }

    @Test
    void editarAPromocaoDepoisNaoMudaOResgate() {
        var promocao = promocaoDe(HOJE, HOJE.plusDays(5));
        gestanteTravada(0);
        service.resgatar(5L, 7L, null);
        var salvo = ArgumentCaptor.forClass(CupomResgatado.class);
        verify(resgatadoRepository).saveAndFlush(salvo.capture());

        promocao.atualizar("Outra", null, new BigDecimal("50"), HOJE, HOJE.plusDays(30), Categoria.ROUPAS);

        assertEquals("15% de desconto em Fraldas", salvo.getValue().getDescricao());
        assertEquals(HOJE.plusDays(5), salvo.getValue().getDataValidade());
    }

    @Test
    void promocaoInexistenteResponde404() {
        when(promocaoRepository.findById(5L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.resgatar(5L, 7L, null));
        assertEquals("Promoção não encontrada", erro.getMessage());
        verify(resgatadoRepository, never()).saveAndFlush(any());
    }

    @Test
    void promocaoInativaEstaIndisponivel() {
        promocaoDe(HOJE, HOJE.plusDays(5)).alterarAtivo(false);
        gestanteTravada(0);

        assertIndisponivel();
    }

    @Test
    void promocaoAntesDoInicioEstaIndisponivel() {
        promocaoDe(HOJE.plusDays(1), HOJE.plusDays(5));
        gestanteTravada(0);

        assertIndisponivel();
    }

    @Test
    void promocaoDepoisDoFimEstaIndisponivel() {
        promocaoDe(HOJE.minusDays(5), HOJE.minusDays(1));
        gestanteTravada(0);

        assertIndisponivel();
    }

    private void assertIndisponivel() {
        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L, null));
        assertEquals("Promoção indisponível", erro.getMessage());
        verify(resgatadoRepository, never()).saveAndFlush(any());
    }

    // Quem decide é o índice único: o serviço não consulta antes, só traduz a violação
    @Test
    void segundoResgateDaMesmaGestanteResponde409() {
        promocaoDe(HOJE, HOJE.plusDays(5));
        gestanteTravada(0);
        when(resgatadoRepository.saveAndFlush(any())).thenThrow(violacao("cupom_resgatado_promocao_uma_vez"));

        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L, UUID.randomUUID()));
        assertEquals("Promoção já resgatada", erro.getMessage());
    }

    @Test
    void chaveRepetidaDevolveOResgateFeitoSemGravarDeNovo() {
        var promocao = promocaoDe(HOJE, HOJE.plusDays(5));
        var gestante = gestanteTravada(40);
        var chave = UUID.randomUUID();
        var feito = new CupomResgatado(promocao, farmacia, gestante, "ABCD2345", chave);
        when(resgatadoRepository.findByGestanteIdAndChaveIdempotencia(7L, chave)).thenReturn(Optional.of(feito));
        // Mesmo que a promoção tenha sido desativada depois: o toque repetido não é um resgate novo
        promocao.alterarAtivo(false);

        var resgate = service.resgatar(5L, 7L, chave);

        assertTrue(resgate.repetido());
        assertEquals("ABCD2345", resgate.cupom().codigo());
        assertEquals(TipoResgate.PROMOCAO, resgate.cupom().tipo());
        assertEquals(40, resgate.cupom().saldo());
        verify(resgatadoRepository, never()).saveAndFlush(any());
    }
}
