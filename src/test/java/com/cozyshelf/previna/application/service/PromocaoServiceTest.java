package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PromocaoServiceTest {
    private static final LocalDate HOJE = LocalDate.now();

    private final PromocaoRepository promocaoRepository = mock(PromocaoRepository.class);
    private final PromocaoService service = new PromocaoService(promocaoRepository);

    private static PromocaoRequest dados(LocalDate inicio, LocalDate fim) {
        return new PromocaoRequest("Semana do bebê", null, new BigDecimal("15"), inicio, fim, Categoria.FRALDAS);
    }

    @Test
    void cadastraParaACategoriaNaEmpresaDoToken() {
        when(promocaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resposta = service.cadastrar(1L, dados(HOJE, HOJE.plusDays(7)));

        var salva = ArgumentCaptor.forClass(Promocao.class);
        verify(promocaoRepository).save(salva.capture());
        assertEquals(Categoria.FRALDAS, salva.getValue().getCategoria());
        assertEquals(Categoria.FRALDAS, resposta.categoria());
    }

    @Test
    void aceitaPromocaoDeUmDiaSo() {
        when(promocaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(HOJE, service.cadastrar(1L, dados(HOJE, HOJE)).dataFim());
    }

    @Test
    void recusaFimAntesDoInicio() {
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(1L, dados(HOJE.plusDays(7), HOJE.plusDays(6))));
        verify(promocaoRepository, never()).save(any());
    }
}
