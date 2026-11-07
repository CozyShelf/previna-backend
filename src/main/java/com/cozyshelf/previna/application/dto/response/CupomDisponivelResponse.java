package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;

import java.math.BigDecimal;
import java.time.LocalDate;

// Cupom que a gestante pode resgatar. imagemProduto e logo são caminhos da API (públicos), nulos quando não há imagem.
public record CupomDisponivelResponse(
        Long id,
        String descricao,
        String produto,
        Categoria categoria,
        String imagemProduto,
        BigDecimal desconto,
        LocalDate dataValidade,
        int pontosNecessarios,
        Empresa empresa
) {
    public record Empresa(Long id, String nome, String segmento, String logo) {}
}
