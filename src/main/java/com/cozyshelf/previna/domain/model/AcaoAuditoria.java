package com.cozyshelf.previna.domain.model;

// O que foi feito, num registro do log de auditoria (RF18). Os mesmos valores do CHECK da V24
public enum AcaoAuditoria {
    // O médico agendou um exame ou consulta (uma linha por item do cadastro)
    AGENDAMENTO_CRIADO,
    // O médico confirmou a realização; o crédito de pontos vai junto
    AGENDAMENTO_REALIZADO,
    // O médico registrou que a gestante não compareceu; sem pontos
    AGENDAMENTO_NAO_COMPARECEU,
    // A gestante trocou pontos por um cupom; o débito vai junto
    CUPOM_RESGATADO,
    // A gestante resgatou uma promoção; sem pontos
    PROMOCAO_RESGATADA,
    // A empresa validou o código do cupom ou da promoção na loja
    RESGATE_VALIDADO
}
