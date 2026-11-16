package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.TipoOferta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Filtros do catálogo de ofertas da gestante, todos opcionais. busca: trecho do nome do produto ou da empresa;
// ateMeusPontos: só produtos com algum cupom que caiba no saldo da gestante do token. A ordem é fixa no servidor.
public record OfertaRequest(
        @Size(max = 100) String busca,
        Categoria categoria,
        Long empresaId,
        TipoOferta tipo,
        Boolean ateMeusPontos,
        @PositiveOrZero Integer pagina,
        @Min(1) @Max(100) Integer tamanho
) {
    // Menor que o das listagens do portal: o app carrega mais a cada rolagem
    private static final int TAMANHO_PADRAO = 10;

    public boolean soAteMeusPontos() {
        return Boolean.TRUE.equals(ateMeusPontos);
    }

    public Pageable paginacao(Sort ordem) {
        return PageRequest.of(pagina == null ? 0 : pagina, tamanho == null ? TAMANHO_PADRAO : tamanho, ordem);
    }
}
