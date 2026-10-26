package com.cozyshelf.previna.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// descricao é montada pelo sistema; produto é o nome do produto
public record CupomResponse(
        Long id,
        String descricao,
        String produto,
        BigDecimal desconto,
        LocalDate dataValidade,
        Integer pontosNecessarios,
        boolean ativo
) {}
