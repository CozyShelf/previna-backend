package com.cozyshelf.previna.application.dto.response;

public record PerfilEmpresaResponse(
        String razaoSocial,
        String cnpj,
        String email,
        String telefone,
        String responsavel,
        String endereco,
        String segmento,
        String descricao,
        String logoUrl
) {}
