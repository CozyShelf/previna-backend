package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.LoginRequest;
import com.cozyshelf.previna.application.dto.response.PerfilResponse;
import com.cozyshelf.previna.application.dto.response.TokenResponse;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
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
        return emitirToken(usuario);
    }

    // Depois do primeiro acesso: o token anterior só servia para trocar as credenciais temporárias
    @Transactional(readOnly = true)
    public TokenResponse renovarToken(Long idUsuario) {
        return emitirToken(usuarioRepository.findById(idUsuario).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PerfilResponse buscarPerfil(Long idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();
        EmpresaParceira empresa = usuario instanceof EmpresaParceira e ? e : null;
        Medico medico = usuario instanceof Medico m ? m : null;
        return new PerfilResponse(usuario.getNome(), usuario.getEmail(), usuario.getLogin(),
                medico != null ? medico.getCrm() : null,
                empresa != null ? empresa.getCnpj() : null,
                empresa != null ? empresa.getLogoUrl() : null,
                medico != null ? medico.podeVerTodasGestantes() : null);
    }

    private TokenResponse emitirToken(Usuario usuario) {
        boolean tutorialPendente = usuario instanceof Gestante gestante && gestante.tutorialPendente();
        return new TokenResponse(jwtService.gerarToken(usuario), usuario.primeiroAcessoPendente(), tutorialPendente);
    }
}
