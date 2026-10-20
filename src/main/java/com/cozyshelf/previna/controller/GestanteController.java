package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.PreCadastroGestanteRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.dto.response.CredenciaisTemporariasResponse;
import com.cozyshelf.previna.application.dto.response.GestanteResumoResponse;
import com.cozyshelf.previna.application.service.GestanteService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/gestantes")
@Tag(name = "Gestantes")
public class GestanteController {
    private final GestanteService gestanteService;

    public GestanteController(GestanteService gestanteService) {
        this.gestanteService = gestanteService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MEDICO')")
    @Operation(summary = "Lista as gestantes acompanhadas pelo médico autenticado")
    public ResponseEntity<List<GestanteResumoResponse>> listar(@AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(gestanteService.listarDoMedico(medico.id()));
    }

    @PostMapping("/pre-cadastro")
    @PreAuthorize("hasRole('MEDICO')")
    @Operation(summary = "Pré-cadastra uma gestante e retorna as credenciais temporárias")
    public ResponseEntity<CredenciaisTemporariasResponse> preCadastrar(
            @RequestBody @Valid PreCadastroGestanteRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gestanteService.preCadastrar(dados, medico.id()));
    }

    @PutMapping("/primeiro-acesso")
    @PreAuthorize("hasRole('GESTANTE')")
    @Operation(summary = "Troca as credenciais temporárias, registra os dados complementares e ativa a conta")
    public ResponseEntity<Void> concluirPrimeiroAcesso(
            @RequestBody @Valid PrimeiroAcessoRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        gestanteService.concluirPrimeiroAcesso(gestante.id(), dados);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/tutorial-concluido")
    @PreAuthorize("hasRole('GESTANTE')")
    @Operation(summary = "Registra que a gestante concluiu o tutorial de uso")
    public ResponseEntity<Void> concluirTutorial(@AuthenticationPrincipal UsuarioAutenticado gestante) {
        gestanteService.concluirTutorial(gestante.id());
        return ResponseEntity.noContent().build();
    }
}
