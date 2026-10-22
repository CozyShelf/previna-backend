package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gestante")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Gestante extends Usuario {
    private static final int DIAS_GESTACAO = 280;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id")
    private Medico medico;

    private LocalDate dataNascimento;
    private String telefone;
    private LocalDate dum;
    private LocalDate dataProvavelParto;
    private int saldoPontos;
    private boolean consentimentoLgpd;
    private LocalDateTime dataConsentimento;
    private boolean tutorialConcluido;

    protected Gestante() {
    }

    public Gestante(String nome, String cpf, String email, String login, String senhaHash,
                    Medico medico, LocalDate dataNascimento, String telefone, LocalDate dum) {
        super(nome, cpf, email, login, senhaHash, Perfil.GESTANTE, StatusConta.PRIMEIRO_ACESSO_PENDENTE);
        this.medico = medico;
        this.dataNascimento = dataNascimento;
        this.telefone = telefone;
        this.dum = dum;
        this.dataProvavelParto = dum.plusDays(DIAS_GESTACAO);
    }

    public String getTelefone() {
        return telefone;
    }

    public LocalDate getDum() {
        return dum;
    }

    public LocalDate getDataProvavelParto() {
        return dataProvavelParto;
    }

    public boolean tutorialPendente() {
        return !tutorialConcluido;
    }

    public void registrarConsentimentoLgpd() {
        this.consentimentoLgpd = true;
        this.dataConsentimento = LocalDateTime.now();
    }

    public void concluirTutorial() {
        this.tutorialConcluido = true;
    }
}
