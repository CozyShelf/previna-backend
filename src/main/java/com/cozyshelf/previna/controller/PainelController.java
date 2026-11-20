package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.PainelAdminResponse;
import com.cozyshelf.previna.application.dto.response.PainelEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.PainelMedicoResponse;
import com.cozyshelf.previna.application.service.PainelAdminService;
import com.cozyshelf.previna.application.service.PainelEmpresaService;
import com.cozyshelf.previna.application.service.PainelMedicoService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Painel da tela Início do portal: uma rota por perfil, cada uma só do seu perfil, com o usuário vindo do token.
// Só leitura; os números e as listas curtas são calculados no banco
@RestController
@RequestMapping("/painel")
@Tag(name = "Painel")
public class PainelController {
    private final PainelMedicoService medicoService;
    private final PainelEmpresaService empresaService;
    private final PainelAdminService adminService;

    public PainelController(PainelMedicoService medicoService, PainelEmpresaService empresaService,
                            PainelAdminService adminService) {
        this.medicoService = medicoService;
        this.empresaService = empresaService;
        this.adminService = adminService;
    }

    @GetMapping("/medico")
    @PreAuthorize("hasRole('MEDICO')")
    @Operation(summary = "Painel do médico: gestantes, atrasados, próximos 7 dias e gestantes sem pendente, no alcance dele")
    public ResponseEntity<PainelMedicoResponse> medico(@AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(medicoService.painel(medico.id()));
    }

    @GetMapping("/empresa")
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Painel da empresa: ofertas, resgates, validações e produtos sem oferta, só da empresa do token")
    public ResponseEntity<PainelEmpresaResponse> empresa(@AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(empresaService.painel(empresa.id()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Painel do admin: contagens, empresas sem primeiro acesso e atividade recente, sem dados de gestantes")
    public ResponseEntity<PainelAdminResponse> admin() {
        return ResponseEntity.ok(adminService.painel());
    }
}
