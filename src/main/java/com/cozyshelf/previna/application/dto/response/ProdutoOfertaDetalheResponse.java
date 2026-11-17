package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Detalhe de um produto do catálogo: todos os cupons válidos (os mais baratos primeiro) e as promoções vigentes
// (as de maior desconto primeiro). imagem e logo são caminhos da API (públicos), nulos sem imagem.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProdutoOfertaDetalheResponse(
        Long id,
        String nome,
        Categoria categoria,
        String imagem,
        Empresa empresa,
        List<Cupom> cupons,
        List<Promocao> promocoes
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Empresa(Long id, String nome, String segmento, String logo) {}

    public record Cupom(Long id, BigDecimal desconto, int pontosNecessarios, LocalDate dataValidade) {}

    // resgatada: a gestante do token já resgatou esta promoção, que só se resgata uma vez
    public record Promocao(Long id, String titulo, BigDecimal desconto, LocalDate dataInicio, LocalDate dataFim,
                           boolean resgatada) {}
}
