package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.DadosSocioeconomicos;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.TransacaoPontosRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PontuacaoServiceTest {
    private final TransacaoPontosRepository transacaoRepository = mock(TransacaoPontosRepository.class);
    private final DadosSocioeconomicosRepository dadosRepository = mock(DadosSocioeconomicosRepository.class);
    private final PontuacaoService service = new PontuacaoService(transacaoRepository, dadosRepository);

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
}
