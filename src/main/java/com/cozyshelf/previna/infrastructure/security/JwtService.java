package com.cozyshelf.previna.infrastructure.security;

import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private static final String CLAIM_PERFIL = "perfil";

    private final SecretKey chave;
    private final Duration expiracao;
    private final Duration expiracaoGestante;

    public JwtService(@Value("${previna.security.jwt.secret}") String segredo,
                      @Value("${previna.security.jwt.expiracao-minutos}") long expiracaoMinutos,
                      @Value("${previna.security.jwt.expiracao-gestante-dias}") long expiracaoGestanteDias) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracao = Duration.ofMinutes(expiracaoMinutos);
        this.expiracaoGestante = Duration.ofDays(expiracaoGestanteDias);
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        // shortcut: token longo e sem revogação (trocar a senha não derruba sessões abertas); usar refresh token quando precisar revogar
        Duration validade = usuario.getPerfil() == Perfil.GESTANTE ? expiracaoGestante : expiracao;
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim(CLAIM_PERFIL, usuario.getPerfil().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade)))
                .signWith(chave)
                .compact();
    }

    public UsuarioAutenticado validarToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UsuarioAutenticado(Long.valueOf(claims.getSubject()), claims.get(CLAIM_PERFIL, String.class));
    }
}
