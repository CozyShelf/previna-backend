package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.*;

public record AtualizacaoPerfilEmpresaRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "\\d{10,11}") String telefone,
        @NotBlank @Size(max = 150) String responsavel,
        @NotBlank @Size(max = 255) String endereco,
        @NotBlank @Size(max = 50) String segmento,
        @Size(max = 1000) String descricao,
        // Wrapper: o campo só vem no multipart quando a empresa remove o logo (ausente = não remover)
        Boolean removerLogo
) {}
