package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.LoginRequest;
import com.cozyshelf.previna.application.dto.response.PerfilResponse;
import com.cozyshelf.previna.application.dto.response.TokenResponse;
import com.cozyshelf.previna.application.service.AuthService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica o usuário e retorna um JWT")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest dados) {
        return ResponseEntity.ok(authService.autenticar(dados));
    }

    @GetMapping("/perfil")
    @Operation(summary = "Retorna o perfil do usuário autenticado")
    public ResponseEntity<PerfilResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(authService.buscarPerfil(usuario.id()));
    }
}
