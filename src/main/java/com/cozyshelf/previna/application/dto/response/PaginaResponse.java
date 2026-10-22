package com.cozyshelf.previna.application.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// pagina começa em 0
public record PaginaResponse<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long total,
        int totalPaginas
) {
    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
