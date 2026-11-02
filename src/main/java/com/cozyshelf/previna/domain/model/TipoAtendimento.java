package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

// Catálogo de exames e consultas do pré-natal. Os tipos da migração V11 são os padrão (criadoPor nulo) e
// valem para todos os médicos; os demais pertencem ao médico que os cadastrou e só aparecem para ele.
@Entity
@Table(name = "tipo_atendimento")
public class TipoAtendimento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Enumerated(EnumType.STRING)
    private CategoriaAtendimento categoria;

    private String descricao;

    // Pontos creditados à gestante na confirmação, antes do fator de ponderação
    private Integer pontosBase;
    private boolean ativo;

    // Médico dono; nulo no tipo padrão, que veio da migração e não tem dono
    private Long criadoPor;

    protected TipoAtendimento() {
    }

    public TipoAtendimento(Long criadoPor, String nome, CategoriaAtendimento categoria, Integer pontosBase,
                           boolean ativo) {
        this.criadoPor = criadoPor;
        this.nome = nome;
        this.categoria = categoria;
        this.pontosBase = pontosBase;
        this.ativo = ativo;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public CategoriaAtendimento getCategoria() {
        return categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getPontosBase() {
        return pontosBase;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Long getCriadoPor() {
        return criadoPor;
    }

    // Tipo da migração V11: todo médico enxerga, nenhum edita
    public boolean padrao() {
        return criadoPor == null;
    }

    // Só o dono escolhe o tipo no cadastro de um agendamento; o padrão vale para todos
    public boolean visivelPara(Long idMedico) {
        return padrao() || criadoPor.equals(idMedico);
    }

    // Desativar é o caminho normal para tirar um tipo de circulação: os agendamentos antigos continuam
    // apontando para ele, e a listagem de escolha deixa de oferecê-lo
    public void atualizar(String nome, CategoriaAtendimento categoria, Integer pontosBase, boolean ativo) {
        this.nome = nome;
        this.categoria = categoria;
        this.pontosBase = pontosBase;
        this.ativo = ativo;
    }
}
