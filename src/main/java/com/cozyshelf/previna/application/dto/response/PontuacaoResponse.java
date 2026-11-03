package com.cozyshelf.previna.application.dto.response;

import java.util.List;

public record PontuacaoResponse(
        int saldo,
        List<AtividadePontuadaResponse> atividades
) {}
