package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgendamentoRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static AgendamentoRequest com(Long gestante, ItemAgendamentoRequest... itens) {
        return new AgendamentoRequest(gestante, itens == null ? null : List.of(itens));
    }

    private static ItemAgendamentoRequest item(Long tipo, LocalDate data, String observacao) {
        return new ItemAgendamentoRequest(tipo, data, observacao);
    }

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
        var dados = com(gestante, item(tipoAtendimento, data, null));
        assertEquals(valido, validator.validate(dados).isEmpty());
    }

    // Vários exames e consultas no mesmo cadastro, cada um com a sua data
    @Test
    void aceitaVariosItens() {
        var dados = com(1L, item(1L, LocalDate.now(), null), item(2L, LocalDate.now().plusDays(30), "Jejum"));
        assertTrue(validator.validate(dados).isEmpty());
    }

    @Test
    void recusaCadastroSemNenhumItem() {
        assertEquals(1, validator.validate(new AgendamentoRequest(1L, List.of())).size());
    }

    @Test
    void recusaCadastroComItensAcimaDoTeto() {
        var itens = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> item((long) i, LocalDate.now(), null))
                .toArray(ItemAgendamentoRequest[]::new);
        assertEquals(1, validator.validate(com(1L, itens)).size());
    }

    // Não há limite superior de data: marcar com muita antecedência é decisão do médico, não erro de validação
    @Test
    void aceitaDataMuitoDistante() {
        assertTrue(validator.validate(com(1L, item(1L, LocalDate.of(3000, 1, 1), null))).isEmpty());
    }

    @Test
    void recusaObservacaoAcimaDoLimite() {
        assertEquals(1, validator.validate(com(1L, item(1L, LocalDate.now(), "x".repeat(1001)))).size());
    }
}
