package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tipo_atendimento")
public class TipoAtendimento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    @Enumerated(EnumType.STRING)
    private CategoriaAtendimento categoria;
    private String descricao;
    // Pontos antes do fator socioeconômico da gestante
    private Integer pontosBase;
    private boolean ativo;

    protected TipoAtendimento() {
    }

    public TipoAtendimento(String nome, CategoriaAtendimento categoria, String descricao, Integer pontosBase) {
        this.nome = nome;
        this.categoria = categoria;
        this.descricao = descricao;
        this.pontosBase = pontosBase;
        this.ativo = true;
    }

    public String getNome() {
        return nome;
    }

    public CategoriaAtendimento getCategoria() {
        return categoria;
    }
}
