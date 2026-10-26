package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promocao")
public class Promocao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long empresaId;
    private String titulo;
    private String descricao;
    // Percentual sobre o preço dos produtos
    private BigDecimal desconto;
    private LocalDate dataInicio;
    private LocalDate dataFim;
    // Vale para todos os produtos da empresa nesta categoria
    @Enumerated(EnumType.STRING)
    private Categoria categoria;

    protected Promocao() {
    }

    public Promocao(Long empresaId, String titulo, String descricao, BigDecimal desconto, LocalDate dataInicio,
                    LocalDate dataFim, Categoria categoria) {
        this.empresaId = empresaId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.desconto = desconto;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.categoria = categoria;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public Categoria getCategoria() {
        return categoria;
    }
}
