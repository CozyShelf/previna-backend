package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;

import java.math.BigDecimal;

public record ProdutoResponse(
        Long id,
        String nome,
        String descricao,
        Categoria categoria,
        BigDecimal preco,
        String imagemUrl
) {}
