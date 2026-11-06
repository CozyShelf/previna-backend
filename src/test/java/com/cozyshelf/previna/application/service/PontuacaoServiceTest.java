package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ExtratoRequest;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.DadosSocioeconomicos;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.TransacaoPontosRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PontuacaoServiceTest {
    private final TransacaoPontosRepository transacaoRepository = mock(TransacaoPontosRepository.class);
    private final DadosSocioeconomicosRepository dadosRepository = mock(DadosSocioeconomicosRepository.class);
    private final GestanteRepository gestanteRepository = mock(GestanteRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PontuacaoService service =
            new PontuacaoService(transacaoRepository, dadosRepository, gestanteRepository, entityManager);

    private final Medico medico = new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false);
    private final Gestante gestante = Campos.definir(
            new Gestante("Maria", "12345678909", null, "12345678909", "hash", medico,
                    LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()), "id", 1L);
    private final TipoAtendimento hemograma =
            new TipoAtendimento(3L, "Hemograma completo", CategoriaAtendimento.EXAME, 25, true);
    private final Agendamento agendamento =
            new Agendamento(gestante, hemograma, medico, LocalDate.now(), null);

    private void comFator(String fator) {
        var dados = Campos.definir(new DadosSocioeconomicos(1L, null, null, null, null),
                "fatorPonderacao", new BigDecimal(fator));
        when(dadosRepository.findById(1L)).thenReturn(Optional.of(dados));
    }

    @Test
    void creditaPontosBaseVezesOFatorArredondado() {
        comFator("1.50");

        // 25 × 1,50 = 37,5, que arredonda para 38 (HALF_UP)
        assertEquals(38, service.creditarPorAgendamento(agendamento));

        assertEquals(38, gestante.getSaldoPontos());
        var salva = ArgumentCaptor.forClass(TransacaoPontos.class);
        verify(transacaoRepository).save(salva.capture());
        assertEquals(TipoTransacao.CREDITO, salva.getValue().getTipo());
        assertEquals(38, salva.getValue().getPontos());
        assertEquals(new BigDecimal("1.50"), salva.getValue().getFatorAplicado());
        assertSame(agendamento, salva.getValue().getAgendamento());
    }

    // Gestante pré-cadastrada ainda não tem dados socioeconômicos: o fator é neutro, não um erro
    @Test
    void usaFatorNeutroQuandoNaoHaDadosSocioeconomicos() {
        when(dadosRepository.findById(1L)).thenReturn(Optional.empty());

        assertEquals(25, service.creditarPorAgendamento(agendamento));

        assertEquals(25, gestante.getSaldoPontos());
        var salva = ArgumentCaptor.forClass(TransacaoPontos.class);
        verify(transacaoRepository).save(salva.capture());
        assertEquals(0, BigDecimal.ONE.compareTo(salva.getValue().getFatorAplicado()));
    }

    @Test
    void somaAoSaldoQueJaExistia() {
        gestante.creditarPontos(100);
        when(dadosRepository.findById(1L)).thenReturn(Optional.empty());

        service.creditarPorAgendamento(agendamento);

        assertEquals(125, gestante.getSaldoPontos());
    }

    @Test
    void arredondaParaBaixoAbaixoDaMetade() {
        comFator("1.01");

        // 25 × 1,01 = 25,25 -> 25
        assertEquals(25, service.creditarPorAgendamento(agendamento));
        verify(transacaoRepository).save(any());
    }

    @Test
    void travaEReleAGestanteAntesDeSomarAoSaldo() {
        when(dadosRepository.findById(1L)).thenReturn(Optional.empty());
        doAnswer(chamada -> {
            // Outro crédito gravou enquanto este esperava a trava: o saldo relido é a base da soma
            gestante.creditarPontos(100);
            return null;
        }).when(entityManager).refresh(gestante, LockModeType.PESSIMISTIC_WRITE);

        service.creditarPorAgendamento(agendamento);

        assertEquals(125, gestante.getSaldoPontos());
    }

    private TransacaoPontos credito(int pontos) {
        return new TransacaoPontos(gestante, agendamento, TipoTransacao.CREDITO, pontos, BigDecimal.ONE);
    }

    private TransacaoPontos debito(int pontos) {
        return new TransacaoPontos(gestante, null, TipoTransacao.DEBITO, pontos, null);
    }

    @Test
    void debitaDoSaldoERegistraODebitoApontandoParaOCupomResgatado() {
        Campos.definir(gestante, "saldoPontos", 500);
        var fralda = new Produto(1L, "Fralda RN", null, Categoria.FRALDAS, new BigDecimal("39.90"));
        var resgatado = new CupomResgatado(new Cupom(1L, fralda, BigDecimal.TEN, LocalDate.now(), 200), gestante,
                "ABCD2345");

        assertEquals(300, service.debitarPontos(gestante, 200, resgatado));

        var salva = ArgumentCaptor.forClass(TransacaoPontos.class);
        verify(transacaoRepository).save(salva.capture());
        assertEquals(TipoTransacao.DEBITO, salva.getValue().getTipo());
        assertEquals(200, salva.getValue().getPontos());
        assertSame(resgatado, salva.getValue().getCupomResgatado());
        assertEquals("Cupom: 10% de desconto em Fralda RN", salva.getValue().getDescricao());
        assertEquals(300, gestante.getSaldoPontos());
    }

    @Test
    void naoDebitaMaisDoQueOSaldoNemDeixaNegativo() {
        Campos.definir(gestante, "saldoPontos", 100);

        assertThrows(IllegalStateException.class, () -> gestante.debitarPontos(101));
        assertEquals(100, gestante.getSaldoPontos());
        gestante.debitarPontos(100);
        assertEquals(0, gestante.getSaldoPontos());
    }

    @Test
    void travaAGestantePelaConsultaComLock() {
        when(gestanteRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.of(gestante));

        assertSame(gestante, service.travarGestante(1L));
        verify(gestanteRepository, never()).findById(any());
    }

    @Test
    void gestanteInexistenteNaTravaResponde404() {
        when(gestanteRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.travarGestante(1L));
        assertEquals("Gestante não encontrada", erro.getMessage());
    }

    @Test
    void debitoSemCupomAntigoContinuaComADescricaoGenerica() {
        assertEquals("Resgate de cupom", debito(10).getDescricao());
    }

    @Test
    void extratoSomaGanhosEGastosEDescreveCadaTransacao() {
        when(transacaoRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(credito(50), debito(30), credito(20)));

        var extrato = service.gerarExtrato(1L, new ExtratoRequest(null, null, null));

        assertEquals(70, extrato.totalGanho());
        assertEquals(30, extrato.totalGasto());
        assertEquals(List.of("Hemograma completo", "Resgate de cupom", "Hemograma completo"),
                extrato.historico().stream().map(t -> t.descricao()).toList());
    }

    @Test
    void extratoSemTransacoesTemTotaisZerados() {
        when(transacaoRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        var extrato = service.gerarExtrato(1L, new ExtratoRequest(TipoTransacao.DEBITO, null, null));

        assertEquals(0, extrato.totalGanho());
        assertEquals(0, extrato.totalGasto());
        assertTrue(extrato.historico().isEmpty());
    }

    @Test
    void periodoComInicioDepoisDoFimEInvalido() {
        var hoje = LocalDate.now();

        assertFalse(new ExtratoRequest(null, hoje, hoje.minusDays(1)).isPeriodoValido());
        assertTrue(new ExtratoRequest(null, hoje, hoje).isPeriodoValido());
        assertTrue(new ExtratoRequest(null, hoje, null).isPeriodoValido());
    }

    @Test
    void pontuacaoTrazOSaldoDaGestanteEAsAtividadesRealizadas() {
        gestante.creditarPontos(120);
        when(gestanteRepository.findById(1L)).thenReturn(Optional.of(gestante));
        when(transacaoRepository.findByGestanteIdAndAgendamentoStatus(eq(1L), eq(StatusAgendamento.REALIZADO), any()))
                .thenReturn(List.of(credito(25)));

        var pontuacao = service.consultarPontuacao(1L);

        assertEquals(120, pontuacao.saldo());
        var atividade = pontuacao.atividades().getFirst();
        assertEquals("Hemograma completo", atividade.nome());
        assertEquals(CategoriaAtendimento.EXAME, atividade.categoria());
        assertEquals(25, atividade.pontos());
    }
}
