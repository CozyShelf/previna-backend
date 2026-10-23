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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {
    private static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    private final JwtService jwtService = new JwtService(SEGREDO, 60, 30);

    @ParameterizedTest
    @CsvSource({
            "MEDICO, false, PT60M, MEDICO",
            "GESTANTE, false, P30D, GESTANTE",
            "GESTANTE, true, PT60M, PRIMEIRO_ACESSO",
            "EMPRESA_PARCEIRA, false, PT60M, EMPRESA_PARCEIRA",
            "EMPRESA_PARCEIRA, true, PT60M, PRIMEIRO_ACESSO",
    })
    void validadeEPapelDependemDoPerfilEDoPrimeiroAcesso(Perfil perfil, boolean primeiroAcessoPendente,
                                                         Duration validade, String papel) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(1L);
        when(usuario.getPerfil()).thenReturn(perfil);
        when(usuario.primeiroAcessoPendente()).thenReturn(primeiroAcessoPendente);
        String token = jwtService.gerarToken(usuario);

        assertEquals(papel, jwtService.validarToken(token).papel());

        Instant expiracao = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .toInstant();

        Instant esperada = Instant.now().plus(validade);
        assertTrue(Duration.between(expiracao, esperada).abs().toSeconds() < 5);
    }
}
