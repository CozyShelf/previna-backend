package com.cozyshelf.previna.application.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

// Lista curta do painel da tela Início: total é a contagem completa e itens, no máximo os ITENS primeiros, para a tela
// mostrar "e mais N". Vem de uma página 0 de tamanho ITENS: a lista e o count são feitos no banco
public record ListaCurtaResponse<T>(long total, List<T> itens) {
    public static final int ITENS = 5;

    public static <T> ListaCurtaResponse<T> de(Page<T> pagina) {
        return new ListaCurtaResponse<>(pagina.getTotalElements(), pagina.getContent());
    }
}
