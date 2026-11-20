package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ValidacaoCupomRequest;
import com.cozyshelf.previna.application.dto.response.Response;
import com.cozyshelf.previna.application.dto.response.ValidacaoCupomResponse;
import com.cozyshelf.previna.application.service.ResgateService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Validação, na loja, do código que a gestante mostra (cupom ou promoção resgatados). A empresa vem do token: uma
// empresa não valida os códigos de outra
@RestController
@PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
@Tag(name = "Cupons")
public class ValidacaoCupomController {
    private final ResgateService resgateService;

    public ValidacaoCupomController(ResgateService resgateService) {
        this.resgateService = resgateService;
    }

    @PostMapping("/empresas/cupons/validar")
    @Operation(summary = "Valida o código de um cupom ou promoção resgatados, que passa a UTILIZADO",
            description = "O código é aceito com espaços nas pontas e em minúsculas. Os erros levam \"codigo\": "
                    + "RESGATE_NAO_ENCONTRADO (inexistente ou de outra empresa), RESGATE_JA_UTILIZADO (com "
                    + "\"dataUso\") ou RESGATE_EXPIRADO.")
    @ApiResponse(responseCode = "200", description = "Código validado")
    @ApiResponse(responseCode = "400", description = "Código vazio")
    @ApiResponse(responseCode = "404", description = "Código inexistente ou de outra empresa",
            content = @Content(schema = @Schema(implementation = Response.class)))
    @ApiResponse(responseCode = "409", description = "Código já utilizado ou expirado",
            content = @Content(schema = @Schema(implementation = Response.class)))
    public ResponseEntity<ValidacaoCupomResponse> validar(
            @RequestBody @Valid ValidacaoCupomRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(resgateService.validar(empresa.id(), dados.codigo()));
    }
}
