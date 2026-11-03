package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;

// Catálogo que o médico escolhe ao cadastrar um exame ou consulta, e que ele também gerencia.
// padrao marca os tipos da migração: aparecem para todos os médicos e nenhum deles pode editar.
public record TipoAtendimentoResponse(
        Long id,
        String nome,
        CategoriaAtendimento categoria,
        Integer pontosBase,
        boolean ativo,
        boolean padrao
) {}
