package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.PreCadastroEmpresaRequest;
import com.cozyshelf.previna.application.dto.response.EmpresaResumoResponse;
import com.cozyshelf.previna.application.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/empresas")
@Tag(name = "Empresas parceiras")
public class EmpresaController {
    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista as empresas parceiras")
    public ResponseEntity<List<EmpresaResumoResponse>> listar() {
        return ResponseEntity.ok(empresaService.listar());
    }

    @PostMapping("/pre-cadastro")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Pré-cadastra uma empresa parceira e envia as credenciais temporárias por e-mail")
    public ResponseEntity<EmpresaResumoResponse> preCadastrar(@RequestBody @Valid PreCadastroEmpresaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaService.preCadastrar(dados));
    }
}
