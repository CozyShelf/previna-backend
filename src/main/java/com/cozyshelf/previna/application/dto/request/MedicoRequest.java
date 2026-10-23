package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;

// Cadastro e edição pelo administrador; senha obrigatória no cadastro, e nula na edição mantém a atual
public record MedicoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @CPF @Pattern(regexp = "\\d{11}") String cpf,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 20) String crm,
        // Exige uma letra para não colidir com os logins temporários (CPF e CNPJ), só de dígitos
        @NotBlank @Pattern(regexp = "(?=.*[A-Za-z])[A-Za-z0-9._-]{4,50}") String login,
        @Size(min = 8, max = 72) @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).*") String senha,
        boolean verTodasGestantes
) {}
