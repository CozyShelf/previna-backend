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
    private String endereco;
    private String logoUrl;
    private String descricao;
    private String segmento;

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

    public String getEndereco() {
        return endereco;
    }

    public String getSegmento() {
        return segmento;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void completarCadastro(String endereco, String descricao, String segmento) {
        this.endereco = endereco;
        this.descricao = descricao;
        this.segmento = segmento;
    }

    public void atualizarContato(String email, String telefone, String responsavel) {
        alterarEmail(email);
        this.telefone = telefone;
        this.responsavel = responsavel;
    }

    public void alterarLogo(String logoUrl) {
        this.logoUrl = logoUrl;
    }
}
