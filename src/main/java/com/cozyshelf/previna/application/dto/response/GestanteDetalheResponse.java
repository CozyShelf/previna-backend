package com.cozyshelf.previna.application.dto.response;

import java.time.LocalDate;

// Dados da gestante na tela do médico; medico é o nome do responsável
public record GestanteDetalheResponse(
        Long id,
        String nome,
        String cpf,
        String telefone,
        String email,
        LocalDate dataNascimento,
        LocalDate dum,
        LocalDate dataProvavelParto,
        int saldoPontos,
        boolean primeiroAcessoPendente,
        String medico
) {}
