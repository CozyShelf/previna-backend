package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ExtratoRequest;
import com.cozyshelf.previna.application.dto.response.ExtratoResponse;
import com.cozyshelf.previna.application.dto.response.PontuacaoResponse;
import com.cozyshelf.previna.application.service.PontuacaoService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A gestante vem do token, não do caminho: uma gestante não vê os pontos de outra
@RestController
@RequestMapping("/gestantes")
@PreAuthorize("hasRole('GESTANTE')")
@Tag(name = "Pontuação")
public class PontuacaoController {
    private final PontuacaoService pontuacaoService;

    public PontuacaoController(PontuacaoService pontuacaoService) {
        this.pontuacaoService = pontuacaoService;
    }

    @GetMapping("/pontuacao")
    @Operation(summary = "Retorna o saldo de pontos da gestante autenticada e as consultas e exames que geraram pontos")
    public ResponseEntity<PontuacaoResponse> pontuacao(@AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(pontuacaoService.consultarPontuacao(gestante.id()));
    }

    @GetMapping("/extrato")
    @Operation(summary = "Lista os pontos ganhos e gastos da gestante autenticada, com os totais; filtra por tipo e período")
    public ResponseEntity<ExtratoResponse> extrato(
            @ModelAttribute @Valid ExtratoRequest filtros,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(pontuacaoService.gerarExtrato(gestante.id(), filtros));
    }
}
