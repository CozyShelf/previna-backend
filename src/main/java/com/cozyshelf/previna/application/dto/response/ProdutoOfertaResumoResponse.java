package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

// Produto no catálogo de ofertas. menorPontos e maiorDescontoCupom: entre os cupons válidos (nulos sem cupom);
// descontoPromocao: o maior entre as promoções vigentes (nulo sem promoção). imagem é caminho da API (público).
// Nulos vão no JSON (ao contrário do resto da API): o app usa o nulo para saber que o selo não aparece.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProdutoOfertaResumoResponse(
        Long id,
        String nome,
        Categoria categoria,
        String imagem,
        Integer menorPontos,
        BigDecimal maiorDescontoCupom,
        BigDecimal descontoPromocao
) {}
