package com.cozyshelf.previna.infrastructure.security;

import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {
    private static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    private final JwtService jwtService = new JwtService(SEGREDO, 60, 30);

    @ParameterizedTest
    @CsvSource({"MEDICO, PT60M", "GESTANTE, P30D"})
    void validadeDependeDoPerfil(Perfil perfil, Duration validade) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(1L);
        when(usuario.getPerfil()).thenReturn(perfil);

        Instant expiracao = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(jwtService.gerarToken(usuario))
                .getPayload()
                .getExpiration()
                .toInstant();

        Instant esperada = Instant.now().plus(validade);
        assertTrue(Duration.between(expiracao, esperada).abs().toSeconds() < 5);
    }
}
