package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // Nenhum arquivo enviado (campo ausente ou vazio) não é erro: só não há imagem
    @Test
    void semArquivoNaoHaImagem() {
        assertTrue(Imagem.de(null).isEmpty());
        assertTrue(Imagem.de(new MockMultipartFile("foto", new byte[0])).isEmpty());
    }

    @Test
    void recusaArquivoQueNaoEImagem() {
        var html = new MockMultipartFile("foto", "foto.png", "image/png", "<html>".getBytes());

        var erro = assertThrows(DadosInvalidosException.class, () -> Imagem.de(html));
        assertEquals("A imagem deve ser PNG, JPEG ou WebP", erro.getMessage());
    }

    // RIFF sem a marca WEBP é outro formato (ex.: WAV, AVI)
    @Test
    void riffQueNaoEWebpNaoEImagem() {
        assertTrue(Imagem.tipoDaImagem(HexFormat.of().parseHex("524946460000000057415645")).isEmpty());
        assertTrue(Imagem.tipoDaImagem(HexFormat.of().parseHex("52494646")).isEmpty());
    }

    @Test
    void falhaNaLeituraDoUploadSobeComoErroDeEntradaESaida() throws IOException {
        MultipartFile quebrado = mock(MultipartFile.class);
        when(quebrado.isEmpty()).thenReturn(false);
        when(quebrado.getBytes()).thenThrow(new IOException("conexão caiu"));

        assertThrows(UncheckedIOException.class, () -> Imagem.de(quebrado));
    }
}
