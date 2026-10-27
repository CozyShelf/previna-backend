package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.TipoAtendimentoResponse;
import com.cozyshelf.previna.application.service.AgendamentoService;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catálogo de exames e consultas, usado pelo médico no cadastro de um agendamento
@RestController
@RequestMapping("/tipos-atendimento")
@PreAuthorize("hasRole('MEDICO')")
@Tag(name = "Tipos de atendimento")
public class TipoAtendimentoController {
    private final AgendamentoService agendamentoService;

    public TipoAtendimentoController(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @GetMapping
    @Operation(summary = "Lista os tipos de atendimento ativos; sem categoria, lista exames e consultas")
    public ResponseEntity<List<TipoAtendimentoResponse>> listar(
            @RequestParam(required = false) CategoriaAtendimento categoria) {
        return ResponseEntity.ok(agendamentoService.listarTiposAtivos(categoria));
    }
}
