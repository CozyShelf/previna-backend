package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;

public record PrimeiroAcessoEmpresaRequest(
        // Exige uma letra para não colidir com os logins temporários (CPF e CNPJ), só de dígitos
        @NotBlank @Pattern(regexp = "(?=.*[A-Za-z])[A-Za-z0-9._-]{4,50}") String novoLogin,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "(?=.*[A-Za-z])(?=.*\\d).*") String novaSenha,
        @NotBlank @Size(max = 255) String endereco,
        @NotBlank @Size(max = 50) String segmento,
        @Size(max = 1000) String descricao
) {}
