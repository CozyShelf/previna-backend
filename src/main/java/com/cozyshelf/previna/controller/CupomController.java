package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.AtivacaoCupomRequest;
import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.response.CupomResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A empresa vem do token, não do caminho: uma empresa não cria cupons em nome de outra
@RestController
@RequestMapping("/empresas/cupons")
@PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
@Tag(name = "Cupons")
public class CupomController {
    private final CupomService cupomService;

    public CupomController(CupomService cupomService) {
        this.cupomService = cupomService;
    }

    @GetMapping
    @Operation(summary = "Lista os cupons da empresa autenticada, os que vencem antes primeiro")
    public ResponseEntity<PaginaResponse<CupomResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(cupomService.listar(empresa.id(), listagem));
    }

    @PostMapping
    @Operation(summary = "Cadastra um cupom para uma categoria (fraldas, roupas, remédios) ou um produto da empresa autenticada")
    public ResponseEntity<CupomResponse> cadastrar(
            @RequestBody @Valid CupomRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cupomService.cadastrar(empresa.id(), dados));
    }

    @PutMapping("/{id}/ativo")
    @Operation(summary = "Ativa ou desativa um cupom da empresa autenticada")
    public ResponseEntity<CupomResponse> alterarAtivo(
            @PathVariable Long id,
            @RequestBody @Valid AtivacaoCupomRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(cupomService.alterarAtivo(empresa.id(), id, dados.ativo()));
    }
}
