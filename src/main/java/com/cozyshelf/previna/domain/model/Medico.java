package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "medico")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Medico extends Usuario {
    private String crm;
    private boolean verTodasGestantes;

    protected Medico() {
    }

    // O administrador define o login e a senha definitivos: o médico não tem primeiro acesso
    public Medico(String nome, String cpf, String email, String crm, String login, String senhaHash,
                  boolean verTodasGestantes) {
        super(nome, cpf, email, login, senhaHash, Perfil.MEDICO, StatusConta.ATIVA);
        this.crm = crm;
        this.verTodasGestantes = verTodasGestantes;
    }

    public String getCrm() {
        return crm;
    }

    // Permissão dada pelo administrador; sem ela o médico só vê as gestantes que acompanha
    public boolean podeVerTodasGestantes() {
        return verTodasGestantes;
    }

    public void atualizar(String nome, String cpf, String email, String crm, String login,
                          boolean verTodasGestantes) {
        alterarDados(nome, cpf, email, login);
        this.crm = crm;
        this.verTodasGestantes = verTodasGestantes;
    }
}
