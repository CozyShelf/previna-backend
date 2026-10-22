package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Busca e página das listagens; a ordem é fixa no servidor para o cliente não ordenar por qualquer campo
public record ListagemRequest(
        @Size(max = 100) String busca,
        @PositiveOrZero Integer pagina,
        @Min(1) @Max(100) Integer tamanho
) {
    private static final int TAMANHO_PADRAO = 20;

    // O id desempata nomes iguais; sem ele, um registro poderia aparecer em duas páginas
    public Pageable ordenadaPor(String campo) {
        return PageRequest.of(pagina == null ? 0 : pagina, tamanho == null ? TAMANHO_PADRAO : tamanho,
                Sort.by(Sort.Order.asc(campo).ignoreCase(), Sort.Order.asc("id")));
    }
}
