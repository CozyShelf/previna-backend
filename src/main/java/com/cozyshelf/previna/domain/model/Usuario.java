package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Concreta: o administrador é só um usuário, sem tabela própria
@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.JOINED)
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cpf;
    private String email;
    private String login;
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    private Perfil perfil;

    @Enumerated(EnumType.STRING)
    private StatusConta statusConta;

    private LocalDateTime criadoEm;

    protected Usuario() {
    }

    protected Usuario(String nome, String cpf, String email, String login, String senhaHash, Perfil perfil, StatusConta statusConta) {
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.login = login;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.statusConta = statusConta;
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getEmail() {
        return email;
    }

    public String getLogin() {
        return login;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public boolean primeiroAcessoPendente() {
        return statusConta == StatusConta.PRIMEIRO_ACESSO_PENDENTE;
    }

    public void concluirPrimeiroAcesso(String login, String senhaHash) {
        this.login = login;
        this.senhaHash = senhaHash;
        this.statusConta = StatusConta.ATIVA;
    }
}
