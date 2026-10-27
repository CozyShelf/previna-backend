package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.application.service.AgendamentoService;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Só o médico cadastra e resolve exames e consultas; a gestante apenas consulta os seus
@RestController
@RequestMapping("/agendamentos")
@PreAuthorize("hasRole('MEDICO')")
@Tag(name = "Agendamentos")
public class AgendamentoController {
    private final AgendamentoService agendamentoService;

    public AgendamentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @PostMapping
    @Operation(summary = "Cadastra um exame ou consulta para a gestante; não credita pontos")
    public ResponseEntity<AgendamentoResponse> cadastrar(
            @RequestBody @Valid AgendamentoRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agendamentoService.agendar(dados, medico.id()));
    }

    @PutMapping("/{id}/confirmar")
    @Operation(summary = "Confirma a realização e credita os pontos do tipo de atendimento à gestante")
    public ResponseEntity<ConfirmacaoResponse> confirmar(@PathVariable Long id,
                                                         @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(agendamentoService.confirmar(id, medico.id()));
    }

    @PutMapping("/{id}/nao-compareceu")
    @Operation(summary = "Registra que a gestante não compareceu; não credita pontos")
    public ResponseEntity<AgendamentoResponse> naoCompareceu(@PathVariable Long id,
                                                             @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(agendamentoService.registrarNaoComparecimento(id, medico.id()));
    }
}
