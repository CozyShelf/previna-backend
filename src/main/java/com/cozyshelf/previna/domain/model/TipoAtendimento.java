package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

// Catálogo de exames e consultas do pré-natal, definido na migração V11
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

    // Pontos creditados à gestante na confirmação, antes do fator de ponderação
    private Integer pontosBase;
    private boolean ativo;

    protected TipoAtendimento() {
    }

    // A aplicação nunca cria um tipo: o catálogo vem da migração. O construtor existe para os testes
    // montarem um tipo sem passar pelo banco, como os demais modelos do projeto.
    public TipoAtendimento(Long id, String nome, CategoriaAtendimento categoria, Integer pontosBase, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.categoria = categoria;
        this.pontosBase = pontosBase;
        this.ativo = ativo;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public CategoriaAtendimento getCategoria() {
        return categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getPontosBase() {
        return pontosBase;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
