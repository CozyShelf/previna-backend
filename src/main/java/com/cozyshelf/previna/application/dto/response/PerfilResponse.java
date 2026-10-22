package com.cozyshelf.previna.application.dto.response;

// crm e verTodasGestantes só para médico; cnpj e logoUrl só para empresa parceira (nulos ficam fora do JSON)
public record PerfilResponse(
    String nome,
    String email,
    String login,
    String crm,
    String cnpj,
    String logoUrl,
    Boolean verTodasGestantes
) {}
