package com.cozyshelf.previna.application.dto.response;

public record EmpresaResumoResponse(
        Long id,
        String razaoSocial,
        String cnpj,
        String email,
        String telefone,
        String responsavel,
        boolean primeiroAcessoPendente
) {}
