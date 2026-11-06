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
    private static final String CLAIM_PRIMEIRO_ACESSO = "primeiroAcesso";
    // Com a senha temporária o papel leva o tipo da conta (PRIMEIRO_ACESSO_GESTANTE, PRIMEIRO_ACESSO_EMPRESA_PARCEIRA):
    // um papel único deixaria a gestante chamar o primeiro acesso da empresa, e vice-versa
    public static final String PAPEL_PRIMEIRO_ACESSO_GESTANTE = "PRIMEIRO_ACESSO_GESTANTE";
    public static final String PAPEL_PRIMEIRO_ACESSO_EMPRESA = "PRIMEIRO_ACESSO_EMPRESA_PARCEIRA";
    private static final String PREFIXO_PRIMEIRO_ACESSO = "PRIMEIRO_ACESSO_";

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
        // Com a senha temporária o token é curto e só conclui o primeiro acesso, que emite um token novo
        boolean temporario = usuario.primeiroAcessoPendente();
        // shortcut: token sem revogação (quando o administrador troca a senha de um médico, as sessões abertas valem até expirar); adicionar versão de credencial no token quando for preciso derrubá-las na hora
        Duration validade = usuario.getPerfil() == Perfil.GESTANTE && !temporario ? expiracaoGestante : expiracao;
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim(CLAIM_PERFIL, usuario.getPerfil().name())
                .claim(CLAIM_PRIMEIRO_ACESSO, temporario)
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
        String perfil = claims.get(CLAIM_PERFIL, String.class);
        String papel = Boolean.TRUE.equals(claims.get(CLAIM_PRIMEIRO_ACESSO, Boolean.class))
                ? PREFIXO_PRIMEIRO_ACESSO + perfil
                : perfil;
        return new UsuarioAutenticado(Long.valueOf(claims.getSubject()), papel);
    }
}
