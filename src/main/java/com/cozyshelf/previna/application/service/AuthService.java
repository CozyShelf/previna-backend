package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.LoginRequest;
import com.cozyshelf.previna.application.dto.response.TokenResponse;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse autenticar(LoginRequest dados) {
        Usuario usuario = usuarioRepository.findByLogin(dados.login())
                .filter(u -> passwordEncoder.matches(dados.senha(), u.getSenhaHash()))
                .orElseThrow(() -> new BadCredentialsException("Login ou senha inválidos"));
        boolean tutorialPendente = usuario instanceof Gestante gestante && gestante.tutorialPendente();
        return new TokenResponse(jwtService.gerarToken(usuario), usuario.primeiroAcessoPendente(), tutorialPendente);
    }
}
