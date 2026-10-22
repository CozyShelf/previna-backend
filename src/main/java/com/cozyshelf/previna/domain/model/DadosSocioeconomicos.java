package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "dados_socioeconomicos")
public class DadosSocioeconomicos {
    @Id
    private Long gestanteId;

    private BigDecimal rendaFamiliar;
    private Integer pessoasDomicilio;

    @Enumerated(EnumType.STRING)
    private Escolaridade escolaridade;

    private Boolean beneficiariaProgramaSocial;

    // shortcut: fator neutro até a regra de ponderação dos pontos ser definida; calcular a partir dos dados acima quando houver regra
    private BigDecimal fatorPonderacao = BigDecimal.ONE;

    protected DadosSocioeconomicos() {
    }

    public DadosSocioeconomicos(Long gestanteId, BigDecimal rendaFamiliar, Integer pessoasDomicilio,
                                Escolaridade escolaridade, Boolean beneficiariaProgramaSocial) {
        this.gestanteId = gestanteId;
        this.rendaFamiliar = rendaFamiliar;
        this.pessoasDomicilio = pessoasDomicilio;
        this.escolaridade = escolaridade;
        this.beneficiariaProgramaSocial = beneficiariaProgramaSocial;
    }

    public Escolaridade getEscolaridade() {
        return escolaridade;
    }
}
