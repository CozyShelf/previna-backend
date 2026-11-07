package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Chega em multipart, junto com a imagem opcional. A regra de qual campo é obrigatório depende do
// tipo (TEXTO exige corpo, os demais exigem mídia) e fica no ConteudoService
public record ConteudoRequest(
        @NotBlank @Size(max = 150) String titulo,
        @NotNull CategoriaConteudo categoria,
        @NotNull TipoConteudo tipo,
        @Size(max = 300) String resumo,
        String corpo,
        @Size(max = 500) String midiaUrl
) {}
