package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "imagem_produto")
public class ImagemProduto {
    @Id
    private Long produtoId;

    private byte[] conteudo;
    private String tipo;

    protected ImagemProduto() {
    }

    public ImagemProduto(Long produtoId, byte[] conteudo, String tipo) {
        this.produtoId = produtoId;
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
