package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cupom")
public class Cupom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long empresaId;

    // O cupom vale só para este produto; desconto geral numa categoria é promoção
    @ManyToOne(fetch = FetchType.LAZY)
    private Produto produto;

    // Percentual sobre o preço
    private BigDecimal desconto;
    private LocalDate dataValidade;
    private Integer pontosNecessarios;
    private boolean ativo;

    protected Cupom() {
    }

    public Cupom(Long empresaId, Produto produto, BigDecimal desconto, LocalDate dataValidade,
                 Integer pontosNecessarios) {
        this.empresaId = empresaId;
        this.produto = produto;
        this.desconto = desconto;
        this.dataValidade = dataValidade;
        this.pontosNecessarios = pontosNecessarios;
        this.ativo = true;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    // Montada na leitura, e não guardada, para acompanhar o nome atual do produto: "12,5% de desconto em Fralda RN"
    public String getDescricao() {
        return "%s%% de desconto em %s".formatted(desconto.stripTrailingZeros().toPlainString().replace('.', ','),
                produto.getNome());
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public LocalDate getDataValidade() {
        return dataValidade;
    }

    public Integer getPontosNecessarios() {
        return pontosNecessarios;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void alterarAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
