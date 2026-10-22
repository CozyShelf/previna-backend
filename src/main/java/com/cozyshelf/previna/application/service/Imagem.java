package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

// Imagem enviada pelo usuário (logo da empresa ou foto da gestante)
public record Imagem(byte[] conteudo, String tipo) {
    // Vazio quando nenhum arquivo foi enviado
    public static Optional<Imagem> de(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return Optional.empty();
        }
        byte[] conteudo = lerBytes(arquivo);
        String tipo = tipoDaImagem(conteudo)
                .orElseThrow(() -> new DadosInvalidosException("A imagem deve ser PNG, JPEG ou WebP"));
        return Optional.of(new Imagem(conteudo, tipo));
    }

    // Tipo pelos primeiros bytes, não pelo que o navegador declara: só PNG, JPEG e WebP (SVG pode carregar script)
    static Optional<String> tipoDaImagem(byte[] conteudo) {
        if (comecaCom(conteudo, 0, 0x89, 'P', 'N', 'G')) return Optional.of("image/png");
        if (comecaCom(conteudo, 0, 0xFF, 0xD8, 0xFF)) return Optional.of("image/jpeg");
        if (comecaCom(conteudo, 0, 'R', 'I', 'F', 'F') && comecaCom(conteudo, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean comecaCom(byte[] conteudo, int inicio, int... assinatura) {
        if (conteudo.length < inicio + assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((conteudo[inicio + i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
