package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.exception.DadosInvalidosException;

import java.util.UUID;
import java.util.regex.Pattern;

// Header Idempotency-Key dos resgates (cupom e promoção): o app gera um UUID a cada confirmação
final class ChaveIdempotencia {
    // Só a forma canônica: o UUID.fromString aceita coisas como "1-2-3-4-5"
    private static final Pattern UUID_CANONICO =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ChaveIdempotencia() {
    }

    // Ausente: resgate sem idempotência (versões antigas do app). Presente, tem de ser um UUID
    static UUID ler(String chave) {
        if (chave == null) {
            return null;
        }
        if (!UUID_CANONICO.matcher(chave).matches()) {
            throw new DadosInvalidosException("Idempotency-Key deve ser um UUID");
        }
        return UUID.fromString(chave);
    }
}
