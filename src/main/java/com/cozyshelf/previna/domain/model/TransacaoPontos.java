package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Extrato de pontos da gestante. Um crédito por agendamento confirmado e um débito por cupom resgatado (agendamento_id
// e cupom_resgatado_id são UNIQUE no banco),
// e fator_aplicado guarda o fator usado para o extrato continuar explicável se a regra de ponderação mudar.
@Entity
@Table(name = "transacao_pontos")
public class TransacaoPontos {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestante_id")
    private Gestante gestante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cupom_resgatado_id")
    private CupomResgatado cupomResgatado;

    @Enumerated(EnumType.STRING)
    private TipoTransacao tipo;

    private int pontos;
    private BigDecimal fatorAplicado;
    private LocalDateTime dataHora;

    protected TransacaoPontos() {
    }

    public TransacaoPontos(Gestante gestante, Agendamento agendamento, TipoTransacao tipo, int pontos,
                           BigDecimal fatorAplicado) {
        this.gestante = gestante;
        this.agendamento = agendamento;
        this.tipo = tipo;
        this.pontos = pontos;
        this.fatorAplicado = fatorAplicado;
        this.dataHora = LocalDateTime.now();
    }

    // Débito pelo resgate de um cupom
    public TransacaoPontos(Gestante gestante, CupomResgatado cupomResgatado, int pontos) {
        this.gestante = gestante;
        this.cupomResgatado = cupomResgatado;
        this.tipo = TipoTransacao.DEBITO;
        this.pontos = pontos;
        this.dataHora = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public int getPontos() {
        return pontos;
    }

    public BigDecimal getFatorAplicado() {
        return fatorAplicado;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public CupomResgatado getCupomResgatado() {
        return cupomResgatado;
    }

    public String getDescricao() {
        if (agendamento != null) {
            return agendamento.getTipo().getNome();
        }
        return cupomResgatado != null ? "Cupom: " + cupomResgatado.getCupom().getDescricao() : "Resgate de cupom";
    }
}
