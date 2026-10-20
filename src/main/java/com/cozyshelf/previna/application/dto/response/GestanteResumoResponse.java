package com.cozyshelf.previna.application.dto.response;

import java.time.LocalDate;

public record GestanteResumoResponse(
        Long id,
        String nome,
        String cpf,
        String telefone,
        LocalDate dum,
        LocalDate dataProvavelParto,
        boolean primeiroAcessoPendente
) {}
