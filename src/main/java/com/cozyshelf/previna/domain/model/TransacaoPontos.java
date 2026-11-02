package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transacao_pontos")
public class TransacaoPontos {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gestanteId;

    // Só no crédito: a consulta ou exame confirmado que gerou os pontos
    @ManyToOne(fetch = FetchType.LAZY)
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    private TipoTransacao tipo;
    // Sempre positivo; o tipo diz se soma ou subtrai do saldo
    private Integer pontos;
    private BigDecimal fatorAplicado;
    private LocalDateTime dataHora;

    protected TransacaoPontos() {
    }

    public TransacaoPontos(Long gestanteId, Agendamento agendamento, TipoTransacao tipo, Integer pontos,
                           BigDecimal fatorAplicado) {
        this.gestanteId = gestanteId;
        this.agendamento = agendamento;
        this.tipo = tipo;
        this.pontos = pontos;
        this.fatorAplicado = fatorAplicado;
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

    public Integer getPontos() {
        return pontos;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    // shortcut: o débito ainda não aponta para o cupom (cupom_resgatado vem com o resgate); trocar pela descrição do cupom resgatado
    public String getDescricao() {
        return agendamento != null ? agendamento.getTipoAtendimento().getNome() : "Resgate de cupom";
    }
}
