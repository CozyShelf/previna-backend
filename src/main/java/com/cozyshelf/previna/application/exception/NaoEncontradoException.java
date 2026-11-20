package com.cozyshelf.previna.application.exception;

public class NaoEncontradoException extends RuntimeException {
    // Nulo nos 404 sem código próprio: a resposta leva só a mensagem
    private final CodigoErro codigo;

    public NaoEncontradoException(String mensagem) {
        this(mensagem, null);
    }

    public NaoEncontradoException(String mensagem, CodigoErro codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    public CodigoErro getCodigo() {
        return codigo;
    }
}
