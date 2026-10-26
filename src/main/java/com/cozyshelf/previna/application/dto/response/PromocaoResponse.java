package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromocaoResponse(
        Long id,
        String titulo,
        String descricao,
        BigDecimal desconto,
        LocalDate dataInicio,
        LocalDate dataFim,
        Categoria categoria
) {}
