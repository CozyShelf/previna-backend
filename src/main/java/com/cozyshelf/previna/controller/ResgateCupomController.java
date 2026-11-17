package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.CupomDisponivelResponse;
import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    @Operation(summary = "Troca pontos da gestante autenticada por um cupom; retorna o código do cupom e o novo saldo",
            description = "Com o header Idempotency-Key, repetir a requisição com a mesma chave não resgata de novo: "
                    + "responde 200 com o resgate já feito e o saldo atual.")
    @ApiResponse(responseCode = "201", description = "Cupom resgatado")
    @ApiResponse(responseCode = "200", description = "Chave já usada: o resgate feito antes, sem novo débito")
    @ApiResponse(responseCode = "400", description = "Idempotency-Key que não é um UUID")
    @ApiResponse(responseCode = "404", description = "Cupom não encontrado")
    @ApiResponse(responseCode = "409", description = "Cupom indisponível (codigo CUPOM_INDISPONIVEL) ou pontos insuficientes (codigo PONTOS_INSUFICIENTES)")
    public ResponseEntity<CupomResgatadoResponse> resgatar(
            @PathVariable Long id,
            @Parameter(description = "UUID gerado pelo app a cada confirmação de resgate; opcional")
            @RequestHeader(name = "Idempotency-Key", required = false) String chave,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        CupomService.Resgate resgate = cupomService.resgatar(id, gestante.id(), ChaveIdempotencia.ler(chave));
        return ResponseEntity.status(resgate.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(resgate.cupom());
    }
}
