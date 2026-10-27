package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;

// Catálogo que o médico escolhe ao cadastrar um exame ou consulta
public record TipoAtendimentoResponse(
        Long id,
        String nome,
        CategoriaAtendimento categoria,
        Integer pontosBase
) {}
