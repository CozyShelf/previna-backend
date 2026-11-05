package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TipoAtendimentoRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // A pontuação é moeda da gestante: um dígito a mais digitado por engano inflaria o saldo dela
    @ParameterizedTest
    @CsvSource({
            "Ultrassom 4D, EXAME, 1, true",
            "Ultrassom 4D, EXAME, 1000, true",
            "Ultrassom 4D, EXAME, 0, false",
            "Ultrassom 4D, EXAME, -5, false",
            "Ultrassom 4D, EXAME, 1001, false",
            "Ultrassom 4D, EXAME, , false",
            "Ultrassom 4D, , 30, false",
            "'', EXAME, 30, false",
            "'   ', EXAME, 30, false",
    })
    void validaCampos(String nome, CategoriaAtendimento categoria, Integer pontosBase, boolean valido) {
        var dados = new TipoAtendimentoRequest(nome, categoria, pontosBase);
        assertEquals(valido, validator.validate(dados).isEmpty());
    }

    @Test
    void recusaNomeAcimaDoLimiteDaColuna() {
        var dados = new TipoAtendimentoRequest("x".repeat(121), CategoriaAtendimento.EXAME, 30);
        assertEquals(1, validator.validate(dados).size());
    }
}
