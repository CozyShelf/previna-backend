package com.cozyshelf.previna.application.dto.response;

public record MedicoResponse(
        Long id,
        String nome,
        String cpf,
        String email,
        String crm,
        String login,
        boolean verTodasGestantes
) {}
