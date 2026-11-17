package com.cozyshelf.previna.application.exception;

// Código estável de um erro de conflito, enviado no campo "codigo" da resposta.
// Os fronts decidem pelo código, nunca pelo texto da mensagem, que pode mudar.
// O JSON leva o nome da constante: renomear uma constante quebra o contrato com o app e o portal
public enum CodigoErro {
    PROMOCAO_JA_RESGATADA,
    PROMOCAO_INDISPONIVEL,
    PROMOCAO_SOBREPOSTA,
    CUPOM_INDISPONIVEL,
    PONTOS_INSUFICIENTES,
    AGENDAMENTO_PENDENTE_REPETIDO
}
