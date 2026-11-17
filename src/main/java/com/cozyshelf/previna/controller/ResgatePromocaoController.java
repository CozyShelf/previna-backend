package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.application.service.PromocaoService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lado da gestante das promoções. A gestante vem do token, não do caminho, como no resgate de cupom
@RestController
@RequestMapping("/promocoes")
@PreAuthorize("hasRole('GESTANTE')")
@Tag(name = "Promoções")
public class ResgatePromocaoController {
    private final PromocaoService promocaoService;

    public ResgatePromocaoController(PromocaoService promocaoService) {
        this.promocaoService = promocaoService;
    }

    @PostMapping("/{id}/resgatar")
    @Operation(summary = "Resgata uma promoção para a gestante autenticada, sem custo em pontos e uma vez só; "
            + "retorna o código, válido até o fim da promoção",
            description = "Com o header Idempotency-Key, repetir a requisição com a mesma chave não resgata de novo: "
                    + "responde 200 com o resgate já feito.")
    @ApiResponse(responseCode = "201", description = "Promoção resgatada")
    @ApiResponse(responseCode = "200", description = "Chave já usada: o resgate feito antes")
    @ApiResponse(responseCode = "400", description = "Idempotency-Key que não é um UUID")
    @ApiResponse(responseCode = "404", description = "Promoção não encontrada")
    @ApiResponse(responseCode = "409", description = "Promoção indisponível, inativa ou fora do período (codigo PROMOCAO_INDISPONIVEL), ou já resgatada (codigo PROMOCAO_JA_RESGATADA)")
    public ResponseEntity<CupomResgatadoResponse> resgatar(
            @PathVariable Long id,
            @Parameter(description = "UUID gerado pelo app a cada confirmação de resgate; opcional")
            @RequestHeader(name = "Idempotency-Key", required = false) String chave,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        CupomService.Resgate resgate = promocaoService.resgatar(id, gestante.id(), ChaveIdempotencia.ler(chave));
        return ResponseEntity.status(resgate.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(resgate.cupom());
    }
}
