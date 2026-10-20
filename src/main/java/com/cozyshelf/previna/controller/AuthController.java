package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.LoginRequest;
import com.cozyshelf.previna.application.dto.response.TokenResponse;
import com.cozyshelf.previna.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
}
