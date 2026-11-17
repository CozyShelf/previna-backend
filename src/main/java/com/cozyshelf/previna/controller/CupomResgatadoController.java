package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.service.ResgateService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// "Meus cupons": os cupons e as promoções resgatados pela gestante do token. Rotas literais sob /gestantes, como
// /gestantes/perfil: o Spring as casa antes de /gestantes/{id} (do médico)
@RestController
@RequestMapping("/gestantes/cupons")
@PreAuthorize("hasRole('GESTANTE')")
@Tag(name = "Cupons")
public class CupomResgatadoController {
    private final ResgateService resgateService;

    public CupomResgatadoController(ResgateService resgateService) {
        this.resgateService = resgateService;
    }

    @GetMapping
    @Operation(summary = "Lista os cupons e as promoções resgatados pela gestante autenticada: ATIVO primeiro, depois "
            + "UTILIZADO e EXPIRADO, do resgate mais recente",
            description = "Antes de listar, os resgates ATIVOS com a validade vencida passam a EXPIRADO.")
    public ResponseEntity<PaginaResponse<ResgateResponse>> listar(
            @ModelAttribute @Valid ResgateRequest filtros,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(resgateService.listar(gestante.id(), filtros));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retorna um cupom ou promoção resgatado pela gestante autenticada, com o código")
    @ApiResponse(responseCode = "200", description = "Resgate da gestante")
    @ApiResponse(responseCode = "404", description = "Resgate inexistente ou de outra gestante")
    public ResponseEntity<ResgateResponse> detalhar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(resgateService.detalhar(gestante.id(), id));
    }
}
