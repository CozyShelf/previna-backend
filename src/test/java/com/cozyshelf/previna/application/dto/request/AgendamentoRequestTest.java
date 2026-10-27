package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgendamentoRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // diasAteAData: deslocamento a partir de hoje; vazio significa data ausente
    @ParameterizedTest
    @CsvSource({
            "1, 1, 0, true",
            "1, 1, 1, true",
            "1, 1, -1, false",
            ", 1, 1, false",
            "1, , 1, false",
            "1, 1, , false",
    })
    void validaCampos(Long gestante, Long tipoAtendimento, Integer diasAteAData, boolean valido) {
        LocalDate data = diasAteAData == null ? null : LocalDate.now().plusDays(diasAteAData);
        var dados = new AgendamentoRequest(gestante, tipoAtendimento, data, null);
        assertEquals(valido, validator.validate(dados).isEmpty());
    }

    // Não há limite superior de data: marcar com muita antecedência é decisão do médico, não erro de validação
    @Test
    void aceitaDataMuitoDistante() {
        var dados = new AgendamentoRequest(1L, 1L, LocalDate.of(3000, 1, 1), null);
        assertTrue(validator.validate(dados).isEmpty());
    }

    @Test
    void recusaObservacaoAcimaDoLimite() {
        var dados = new AgendamentoRequest(1L, 1L, LocalDate.now(), "x".repeat(1001));
        assertEquals(1, validator.validate(dados).size());
    }
}
