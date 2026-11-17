package com.cozyshelf.previna.application.exception;

public class ConflitoException extends RuntimeException {
    // Nulo nos conflitos sem código próprio: a resposta leva só a mensagem
    private final CodigoErro codigo;

    public ConflitoException(String mensagem) {
        this(mensagem, null);
    }

    public ConflitoException(String mensagem, CodigoErro codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    public CodigoErro getCodigo() {
        return codigo;
    }
}
