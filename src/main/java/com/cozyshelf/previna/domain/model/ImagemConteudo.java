package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "imagem_conteudo")
public class ImagemConteudo {
    @Id
    private Long conteudoId;

    private byte[] conteudo;
    private String tipo;

    protected ImagemConteudo() {
    }

    public ImagemConteudo(Long conteudoId, byte[] conteudo, String tipo) {
        this.conteudoId = conteudoId;
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
