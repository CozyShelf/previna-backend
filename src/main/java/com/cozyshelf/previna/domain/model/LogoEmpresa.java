package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "logo_empresa")
public class LogoEmpresa {
    @Id
    private Long empresaId;

    private byte[] conteudo;
    private String tipo;

    protected LogoEmpresa() {
    }

    public LogoEmpresa(Long empresaId, byte[] conteudo, String tipo) {
        this.empresaId = empresaId;
        this.conteudo = conteudo;
        this.tipo = tipo;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public String getTipo() {
        return tipo;
    }
}
