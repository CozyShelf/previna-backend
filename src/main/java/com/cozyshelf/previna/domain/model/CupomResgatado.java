package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Cupom trocado pela gestante. Guarda uma cópia do cupom no momento do resgate (produto, empresa, desconto, validade
// e pontos): a empresa pode editar o cupom ou renomear o produto depois sem mudar o que a gestante resgatou.
@Entity
@Table(name = "cupom_resgatado")
public class CupomResgatado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // De onde o resgate veio; o que se mostra à gestante é a cópia abaixo, e não o cupom como está hoje
    @ManyToOne(fetch = FetchType.LAZY)
    private Cupom cupom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestante_id")
    private Gestante gestante;

    private String codigo;

    @Enumerated(EnumType.STRING)
    private StatusCupomResgatado status;

    private LocalDateTime dataResgate;
    private LocalDateTime dataUso;

    // ---- Cópia do cupom no momento do resgate ----
    private Long produtoId;
    private String produtoNome;
    private Long empresaId;
    private String empresaNome;
    // Percentual sobre o preço
    private BigDecimal desconto;
    private LocalDate dataValidade;
    private int pontosGastos;

    protected CupomResgatado() {
    }

    public CupomResgatado(Cupom cupom, EmpresaParceira empresa, Gestante gestante, String codigo) {
        this.cupom = cupom;
        this.gestante = gestante;
        this.codigo = codigo;
        this.status = StatusCupomResgatado.ATIVO;
        this.dataResgate = LocalDateTime.now();
        this.produtoId = cupom.getProduto().getId();
        this.produtoNome = cupom.getProduto().getNome();
        this.empresaId = empresa.getId();
        this.empresaNome = empresa.getRazaoSocial();
        this.desconto = cupom.getDesconto();
        this.dataValidade = cupom.getDataValidade();
        this.pontosGastos = cupom.getPontosNecessarios();
    }

    public Long getId() {
        return id;
    }

    public Cupom getCupom() {
        return cupom;
    }

    public Gestante getGestante() {
        return gestante;
    }

    public String getCodigo() {
        return codigo;
    }

    public StatusCupomResgatado getStatus() {
        return status;
    }

    public LocalDateTime getDataResgate() {
        return dataResgate;
    }

    public LocalDateTime getDataUso() {
        return dataUso;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public String getEmpresaNome() {
        return empresaNome;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public LocalDate getDataValidade() {
        return dataValidade;
    }

    public int getPontosGastos() {
        return pontosGastos;
    }

    // A mesma frase do cupom, com os dados do resgate: "10% de desconto em Fralda RN"
    public String getDescricao() {
        return Cupom.descricao(desconto, produtoNome);
    }
}
