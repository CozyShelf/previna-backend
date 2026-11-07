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

    // Do mais recente para o mais antigo em todas as listagens. O id cresce a cada registro gravado,
    // então ele é a ordem de cadastro e, por ser único, nenhum registro aparece em duas páginas
    public Pageable maisRecentes() {
        return PageRequest.of(pagina == null ? 0 : pagina, tamanho == null ? TAMANHO_PADRAO : tamanho,
                Sort.by(Sort.Order.desc("id")));
    }

    // Para as consultas que já trazem o próprio ORDER BY (ex.: a ordem aleatória dos conteúdos):
    // um Sort aqui seria acrescentado depois do da consulta e desfaria o sorteio
    public Pageable semOrdem() {
        return PageRequest.of(pagina == null ? 0 : pagina, tamanho == null ? TAMANHO_PADRAO : tamanho);
    }
}
