package com.cozyshelf.previna.application.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class CredencialService {
    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TAMANHO_SENHA = 8;

    private final SecureRandom random = new SecureRandom();

    public String gerarSenhaAleatoria() {
        StringBuilder senha = new StringBuilder(TAMANHO_SENHA);
        for (int i = 0; i < TAMANHO_SENHA; i++) {
            senha.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }
        return senha.toString();
    }
}
