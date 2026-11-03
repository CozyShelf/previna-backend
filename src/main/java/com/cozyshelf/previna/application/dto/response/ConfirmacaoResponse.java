package com.cozyshelf.previna.application.dto.response;

// Resposta da confirmação: o agendamento atualizado, quanto foi creditado e o saldo que a gestante ficou
public record ConfirmacaoResponse(
        AgendamentoResponse agendamento,
        int pontosCreditados,
        int saldoPontos
) {}
