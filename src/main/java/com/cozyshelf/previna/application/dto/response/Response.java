package com.cozyshelf.previna.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

// "codigo" só aparece nos erros que têm código estável (ver CodigoErro); sem ele, o JSON é só {"message": ...}
public record Response (
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) String codigo
) {
    public Response(String message) {
        this(message, null);
    }
}
