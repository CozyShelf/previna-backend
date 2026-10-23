package com.cozyshelf.previna.application.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImagemTest {
    @ParameterizedTest
    @CsvSource({
            "89504e470d0a1a0a, image/png",
            "ffd8ffe000104a46, image/jpeg",
            "524946460000000057454250, image/webp",
            "4749463839610100, ''",
            "3c68746d6c3e, ''",
            "8950, ''",
    })
    void identificaTipoPeloConteudo(String hex, String tipo) {
        assertEquals(tipo, Imagem.tipoDaImagem(HexFormat.of().parseHex(hex)).orElse(""));
    }
}
