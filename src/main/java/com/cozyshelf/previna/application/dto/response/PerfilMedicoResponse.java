package com.cozyshelf.previna.application.dto.response;

public record PerfilMedicoResponse(
    String nome,
    String email,
    String login,
    String crm
) {}
