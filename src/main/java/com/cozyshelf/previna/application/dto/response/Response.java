package com.cozyshelf.previna.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// Corpo dos erros. "codigo" só aparece nos erros que têm código estável (ver CodigoErro); "dataUso", só no código de
// resgate já validado. Sem eles, o JSON é só {"message": ...}
public record Response (
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) String codigo,
    @JsonInclude(JsonInclude.Include.NON_NULL) Instant dataUso
) {
    public Response(String message) {
        this(message, null, null);
    }

    public Response(String message, String codigo) {
        this(message, codigo, null);
    }
}
