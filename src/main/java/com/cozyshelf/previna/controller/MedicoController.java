package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.PerfilMedicoResponse;
import com.cozyshelf.previna.application.service.MedicoService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/medicos")
@Tag(name = "Médicos")
public class MedicoController {
    private final MedicoService medicoService;

    public MedicoController(MedicoService medicoService) {
        this.medicoService = medicoService;
    }

    @GetMapping("/perfil")
    @PreAuthorize("hasRole('MEDICO')")
    @Operation(summary = "Retorna o perfil do médico autenticado")
    public ResponseEntity<PerfilMedicoResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(medicoService.buscarPerfil(medico.id()));
    }
}
