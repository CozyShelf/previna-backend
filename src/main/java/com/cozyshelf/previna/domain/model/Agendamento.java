package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Consulta ou exame atribuído pelo médico; os pontos são creditados quando ele confirma a realização
@Entity
@Table(name = "agendamento")
public class Agendamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long gestanteId;

    @ManyToOne(fetch = FetchType.LAZY)
    private TipoAtendimento tipoAtendimento;

    private Long atribuidoPor;
    private Long confirmadoPor;
    private LocalDate dataPrevista;
    @Enumerated(EnumType.STRING)
    private StatusAgendamento status;
    private LocalDateTime dataConfirmacao;
    private String observacao;

    protected Agendamento() {
    }

    public Agendamento(Long gestanteId, TipoAtendimento tipoAtendimento, Long atribuidoPor, LocalDate dataPrevista) {
        this.gestanteId = gestanteId;
        this.tipoAtendimento = tipoAtendimento;
        this.atribuidoPor = atribuidoPor;
        this.dataPrevista = dataPrevista;
        this.status = StatusAgendamento.AGENDADO;
    }

    public Long getId() {
        return id;
    }

    public TipoAtendimento getTipoAtendimento() {
        return tipoAtendimento;
    }
}
