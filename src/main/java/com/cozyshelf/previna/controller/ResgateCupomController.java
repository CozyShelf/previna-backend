package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.CupomDisponivelResponse;
import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Lado da gestante dos cupons. A gestante vem do token, não do caminho: uma gestante não resgata com os pontos de outra
@RestController
@RequestMapping("/cupons")
@PreAuthorize("hasRole('GESTANTE')")
@Tag(name = "Cupons")
public class ResgateCupomController {
    private final CupomService cupomService;

    public ResgateCupomController(CupomService cupomService) {
        this.cupomService = cupomService;
    }

    @GetMapping("/disponiveis")
    @Operation(summary = "Lista os cupons ativos e dentro da validade de todas as empresas parceiras, os mais baratos primeiro")
    public ResponseEntity<List<CupomDisponivelResponse>> disponiveis() {
        return ResponseEntity.ok(cupomService.listarDisponiveis());
    }

    @PostMapping("/{id}/resgatar")
    @Operation(summary = "Troca pontos da gestante autenticada por um cupom; retorna o código do cupom e o novo saldo")
    public ResponseEntity<CupomResgatadoResponse> resgatar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cupomService.resgatar(id, gestante.id()));
    }
}
