package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CNPJ;

public record PreCadastroEmpresaRequest(
        @NotBlank @Size(max = 150) String razaoSocial,
        // shortcut: só CNPJ numérico (o @CNPJ do Hibernate Validator 8 não conhece o alfanumérico emitido desde jul/2026); trocar quando o validator suportar
        // O lookahead recusa dígitos todos iguais, que o @CNPJ aceita (ex.: 00000000000000)
        @NotBlank @CNPJ @Pattern(regexp = "(?!(\\d)\\1{13})\\d{14}") String cnpj,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "\\d{10,11}") String telefone,
        @NotBlank @Size(max = 150) String responsavel
) {}
