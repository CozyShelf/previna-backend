package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PromocaoResponse;
import com.cozyshelf.previna.application.service.PromocaoService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A empresa vem do token, não do caminho: uma empresa não cria promoções em nome de outra
@RestController
@RequestMapping("/empresas/promocoes")
@PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
@Tag(name = "Promoções")
public class PromocaoController {
    private final PromocaoService promocaoService;

    public PromocaoController(PromocaoService promocaoService) {
        this.promocaoService = promocaoService;
    }

    @GetMapping
    @Operation(summary = "Lista as promoções da empresa autenticada; busca pelo título")
    public ResponseEntity<PaginaResponse<PromocaoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(promocaoService.listar(empresa.id(), listagem));
    }

    @PostMapping
    @Operation(summary = "Cadastra uma promoção para uma categoria de produtos da empresa autenticada")
    public ResponseEntity<PromocaoResponse> cadastrar(
            @RequestBody @Valid PromocaoRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promocaoService.cadastrar(empresa.id(), dados));
    }
}
