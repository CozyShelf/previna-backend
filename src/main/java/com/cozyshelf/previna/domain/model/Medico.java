package com.cozyshelf.previna.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "medico")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Medico extends Usuario {
    private String crm;

    protected Medico() {
    }

    public String getCrm() {
        return crm;
    }
}
