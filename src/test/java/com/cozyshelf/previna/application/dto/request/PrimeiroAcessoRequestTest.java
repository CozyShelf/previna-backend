package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrimeiroAcessoRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @CsvSource({
            "maria.silva, senha123, true, , , true",
            "maria.silva, senha123, true, 2500, 3, true",
            "maria.silva, abcdefgh, true, , , false",
            "maria.silva, 12345678, true, , , false",
            "maria.silva, abc123, true, , , false",
            "ma, senha123, true, , , false",
            "maria silva, senha123, true, , , false",
            "maria.silva, senha123, false, , , false",
            "maria.silva, senha123, true, -1, , false",
            "maria.silva, senha123, true, , 0, false",
    })
    void validaCampos(String login, String senha, boolean consentimento, BigDecimal renda, Integer pessoas,
                      boolean valido) {
        var dados = new PrimeiroAcessoRequest(login, senha, consentimento, renda, pessoas, null, null);
        assertEquals(valido, validator.validate(dados).isEmpty());
    }
}
