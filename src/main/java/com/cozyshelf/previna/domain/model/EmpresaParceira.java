package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresa_parceira")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class EmpresaParceira extends Usuario {
    private String razaoSocial;
    private String cnpj;
    private String telefone;
    private String responsavel;

    protected EmpresaParceira() {
    }

    public EmpresaParceira(String razaoSocial, String cnpj, String email, String telefone, String responsavel,
                           String login, String senhaHash) {
        super(razaoSocial, null, email, login, senhaHash, Perfil.EMPRESA_PARCEIRA, StatusConta.PRIMEIRO_ACESSO_PENDENTE);
        this.razaoSocial = razaoSocial;
        this.cnpj = cnpj;
        this.telefone = telefone;
        this.responsavel = responsavel;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getResponsavel() {
        return responsavel;
    }
}
