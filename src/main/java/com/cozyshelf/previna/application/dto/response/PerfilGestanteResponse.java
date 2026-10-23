package com.cozyshelf.previna.application.dto.response;

public record PerfilGestanteResponse(
        String nome,
        String email,
        String telefone,
        boolean temFoto
) {}
