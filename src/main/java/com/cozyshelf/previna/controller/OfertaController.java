package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.OfertaRequest;
import com.cozyshelf.previna.application.dto.response.OfertaEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaDetalheResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaResumoResponse;
import com.cozyshelf.previna.application.service.OfertaService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
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

// Catálogo de cupons e promoções da gestante. O saldo do filtro "cabe no meu saldo" é o da gestante do token
@RestController
@RequestMapping("/ofertas")
@PreAuthorize("hasRole('GESTANTE')")
@Tag(name = "Ofertas")
public class OfertaController {
    private final OfertaService ofertaService;

    public OfertaController(OfertaService ofertaService) {
        this.ofertaService = ofertaService;
    }

    @GetMapping
    @Operation(summary = "Lista as empresas com produtos em oferta (cupom válido ou promoção vigente), por nome, "
            + "cada uma com até 10 produtos e o total deles")
    public ResponseEntity<PaginaResponse<OfertaEmpresaResponse>> listar(
            @ModelAttribute @Valid OfertaRequest filtros,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(ofertaService.listarEmpresas(gestante.id(), filtros));
    }

    @GetMapping("/empresas/{empresaId}/produtos")
    @Operation(summary = "Lista os produtos em oferta de uma empresa, por nome, com os mesmos filtros do catálogo")
    public ResponseEntity<PaginaResponse<ProdutoOfertaResumoResponse>> listarProdutos(
            @PathVariable Long empresaId,
            @ModelAttribute @Valid OfertaRequest filtros,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(ofertaService.listarProdutos(gestante.id(), empresaId, filtros));
    }

    @GetMapping("/produtos/{id}")
    @Operation(summary = "Retorna um produto em oferta com todos os cupons válidos e as promoções vigentes")
    public ResponseEntity<ProdutoOfertaDetalheResponse> detalhar(@PathVariable Long id) {
        return ResponseEntity.ok(ofertaService.detalhar(id));
    }
}
