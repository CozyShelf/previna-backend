package com.cozyshelf.previna.application.dto.response;

// crm só para médico; cnpj só para empresa parceira (nulos ficam fora do JSON)
public record PerfilResponse(
    String nome,
    String email,
    String login,
    String crm,
    String cnpj
) {}
