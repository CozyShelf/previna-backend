package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cupom_resgatado")
public class CupomResgatado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    protected CupomResgatado() {
    }

    public CupomResgatado(Cupom cupom, Gestante gestante, String codigo) {
        this.cupom = cupom;
        this.gestante = gestante;
        this.codigo = codigo;
        this.status = StatusCupomResgatado.ATIVO;
        this.dataResgate = LocalDateTime.now();
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
}
