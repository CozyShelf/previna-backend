package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.TipoConteudo;

import java.time.LocalDateTime;

// publicadoEm só existe enquanto o conteúdo está publicado
public record ConteudoResponse(
        Long id,
        String titulo,
        CategoriaConteudo categoria,
        TipoConteudo tipo,
        String resumo,
        String corpo,
        String midiaUrl,
        boolean publicado,
        LocalDateTime criadoEm,
        LocalDateTime publicadoEm
) {}
