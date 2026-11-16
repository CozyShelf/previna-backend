package com.cozyshelf.previna.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

// Empresa no catálogo de ofertas, com os primeiros produtos com oferta (por nome) e quantos são ao todo, para o
// "Ver todos". logo é caminho da API (público), nulo sem logo.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OfertaEmpresaResponse(
        Long id,
        String nome,
        String segmento,
        String logo,
        long totalProdutos,
        List<ProdutoOfertaResumoResponse> produtos
) {}
