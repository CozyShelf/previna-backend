package com.cozyshelf.previna.application.dto.response;

import java.util.List;

// Totais das transações do histórico (já filtrado), as mais recentes primeiro
public record ExtratoResponse(
        int totalGanho,
        int totalGasto,
        List<TransacaoResponse> historico
) {}
