package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;

// Linha da tela inicial do médico: o exame ou consulta pendente de hoje e a gestante que vai fazê-lo.
// Sem horário porque agendamento só guarda a data; a ordem é a de cadastro.
public record AtendimentoDoDiaResponse(
        Long id,
        Long gestante,
        String nomeGestante,
        String tipo,
        CategoriaAtendimento categoria
) {}
