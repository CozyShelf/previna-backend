package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "foto_gestante")
public class FotoGestante {
    @Id
    private Long gestanteId;

    private byte[] conteudo;
    private String tipo;

    protected FotoGestante() {
    }

    public FotoGestante(Long gestanteId, byte[] conteudo, String tipo) {
        this.gestanteId = gestanteId;
        this.conteudo = conteudo;
        this.tipo = tipo;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public String getTipo() {
        return tipo;
    }
}
