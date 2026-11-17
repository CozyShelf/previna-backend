package com.cozyshelf.previna.domain.model;

// Categoria geral do produto; a promoção vale para todos os produtos de uma categoria
public enum Categoria {
    FRALDAS("Fraldas"),
    ROUPAS("Roupas"),
    REMEDIOS("Remédios");

    // Como aparece nas frases mostradas à gestante: "15% de desconto em Fraldas"
    private final String nome;

    Categoria(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }
}
