package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.MedicoRequest;
import com.cozyshelf.previna.application.dto.response.MedicoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.service.MedicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Só o administrador cadastra e edita médicos, inclusive o login e a senha
@RestController
@RequestMapping("/medicos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Médicos")
public class MedicoController {
    private final MedicoService medicoService;

    public MedicoController(MedicoService medicoService) {
        this.medicoService = medicoService;
    }

    @GetMapping
    @Operation(summary = "Lista os médicos; busca por nome, CPF, CRM, e-mail ou login")
    public ResponseEntity<PaginaResponse<MedicoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @RequestParam(required = false) Boolean verTodasGestantes) {
        return ResponseEntity.ok(medicoService.listar(listagem, verTodasGestantes));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retorna os dados de um médico")
    public ResponseEntity<MedicoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(medicoService.buscar(id));
    }

    @PostMapping
    @Operation(summary = "Cadastra um médico com o login e a senha definidos pelo administrador")
    public ResponseEntity<MedicoResponse> cadastrar(@RequestBody @Valid MedicoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicoService.cadastrar(dados));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza os dados e o login de um médico; troca a senha quando ela é informada")
    public ResponseEntity<MedicoResponse> atualizar(@PathVariable Long id, @RequestBody @Valid MedicoRequest dados) {
        return ResponseEntity.ok(medicoService.atualizar(id, dados));
    }
}
