package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ExtratoRequest;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.TransacaoPontosRepository;
import org.junit.jupiter.api.Test;
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
    private final GestanteRepository gestanteRepository = mock(GestanteRepository.class);
    private final PontuacaoService service = new PontuacaoService(transacaoRepository, gestanteRepository);

    private final Agendamento ultrassom = new Agendamento(1L,
            new TipoAtendimento("Ultrassom morfológico", CategoriaAtendimento.EXAME, null, 50), 2L, LocalDate.now());

    private static TransacaoPontos credito(Agendamento agendamento, int pontos) {
        return new TransacaoPontos(1L, agendamento, TipoTransacao.CREDITO, pontos, BigDecimal.ONE);
    }

    private static TransacaoPontos debito(int pontos) {
        return new TransacaoPontos(1L, null, TipoTransacao.DEBITO, pontos, null);
    }

    @Test
    void extratoSomaGanhosEGastosEDescreveCadaTransacao() {
        when(transacaoRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(credito(ultrassom, 50), debito(30), credito(ultrassom, 20)));

        var extrato = service.gerarExtrato(1L, new ExtratoRequest(null, null, null));

        assertEquals(70, extrato.totalGanho());
        assertEquals(30, extrato.totalGasto());
        assertEquals(List.of("Ultrassom morfológico", "Resgate de cupom", "Ultrassom morfológico"),
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
        var gestante = mock(Gestante.class);
        when(gestante.getSaldoPontos()).thenReturn(120);
        when(gestanteRepository.findById(1L)).thenReturn(Optional.of(gestante));
        when(transacaoRepository.findByGestanteIdAndAgendamentoStatus(eq(1L), eq(StatusAgendamento.REALIZADO), any()))
                .thenReturn(List.of(credito(ultrassom, 50)));

        var pontuacao = service.consultarPontuacao(1L);

        assertEquals(120, pontuacao.saldo());
        var atividade = pontuacao.atividades().getFirst();
        assertEquals("Ultrassom morfológico", atividade.nome());
        assertEquals(CategoriaAtendimento.EXAME, atividade.categoria());
        assertEquals(50, atividade.pontos());
    }
}
