package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;

public record AtualizacaoPerfilGestanteRequest(
        @NotBlank @Pattern(regexp = "\\d{10,11}") String telefone,
        @Email @Size(max = 150) String email,
        // Wrapper: o campo só vem no multipart quando a gestante remove a foto (ausente = não remover)
        Boolean removerFoto
) {
    // E-mail é opcional; vazio vira nulo para não esbarrar no UNIQUE de outra gestante sem e-mail
    public String emailOuNulo() {
        return email == null || email.isBlank() ? null : email;
    }
}
