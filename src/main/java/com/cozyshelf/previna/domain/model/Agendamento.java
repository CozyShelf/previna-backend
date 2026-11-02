package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Exame ou consulta marcado pelo médico para a gestante. Nasce AGENDADO e só sai desse status
// quando o médico registra o desfecho; o cadastro não credita pontos.
@Entity
@Table(name = "agendamento")
public class Agendamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JoinColumn explícito: a PK de gestante e de medico é usuario_id, e o nome gerado não bateria com a migração
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestante_id")
    private Gestante gestante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_atendimento_id")
    private TipoAtendimento tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atribuido_por")
    private Medico atribuidoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por")
    private Medico confirmadoPor;

    private LocalDate dataPrevista;

    @Enumerated(EnumType.STRING)
    private StatusAgendamento status;

    private LocalDateTime dataConfirmacao;
    private String observacao;

    protected Agendamento() {
    }

    public Agendamento(Gestante gestante, TipoAtendimento tipo, Medico atribuidoPor, LocalDate dataPrevista,
                       String observacao) {
        this.gestante = gestante;
        this.tipo = tipo;
        this.atribuidoPor = atribuidoPor;
        this.dataPrevista = dataPrevista;
        this.observacao = observacao;
        this.status = StatusAgendamento.AGENDADO;
    }

    public Long getId() {
        return id;
    }

    public Gestante getGestante() {
        return gestante;
    }

    public TipoAtendimento getTipo() {
        return tipo;
    }

    public Medico getAtribuidoPor() {
        return atribuidoPor;
    }

    public Medico getConfirmadoPor() {
        return confirmadoPor;
    }

    public LocalDate getDataPrevista() {
        return dataPrevista;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public LocalDateTime getDataConfirmacao() {
        return dataConfirmacao;
    }

    public String getObservacao() {
        return observacao;
    }

    public boolean pendente() {
        return status == StatusAgendamento.AGENDADO;
    }

    // Desfecho registrado pelo médico: realizado ou não compareceu. Sempre guarda quem registrou e quando.
    public void registrarDesfecho(StatusAgendamento status, Medico confirmadoPor) {
        this.status = status;
        this.confirmadoPor = confirmadoPor;
        this.dataConfirmacao = LocalDateTime.now();
    }
}
