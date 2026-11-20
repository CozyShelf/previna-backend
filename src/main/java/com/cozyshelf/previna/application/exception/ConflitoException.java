package com.cozyshelf.previna.application.exception;

import java.time.Instant;

public class ConflitoException extends RuntimeException {
    // Nulo nos conflitos sem código próprio: a resposta leva só a mensagem
    private final CodigoErro codigo;
    // Quando o que conflita é um uso já registrado (código de resgate já validado): vai no corpo como "dataUso".
    // Nulo nos demais conflitos, e então o campo não aparece
    private final Instant dataUso;

    public ConflitoException(String mensagem) {
        this(mensagem, null);
    }

    public ConflitoException(String mensagem, CodigoErro codigo) {
        this(mensagem, codigo, null);
    }

    public ConflitoException(String mensagem, CodigoErro codigo, Instant dataUso) {
        super(mensagem);
        this.codigo = codigo;
        this.dataUso = dataUso;
    }

    public CodigoErro getCodigo() {
        return codigo;
    }

    public Instant getDataUso() {
        return dataUso;
    }
}
