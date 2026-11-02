package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.TipoAtendimentoRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.Response;
import com.cozyshelf.previna.application.dto.response.TipoAtendimentoResponse;
import com.cozyshelf.previna.application.service.TipoAtendimentoService;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Catálogo de exames e consultas. O médico vem do token, nunca do caminho: ninguém cadastra tipo
// em nome de outro médico.
@RestController
@RequestMapping("/tipos-atendimento")
@PreAuthorize("hasRole('MEDICO')")
@Tag(name = "Tipos de atendimento")
public class TipoAtendimentoController {
    private final TipoAtendimentoService tipoService;

    public TipoAtendimentoController(TipoAtendimentoService tipoService) {
        this.tipoService = tipoService;
    }

    @GetMapping
    @Operation(summary = "Lista os tipos ativos que o médico pode escolher: os padrão e os dele")
    public ResponseEntity<List<TipoAtendimentoResponse>> disponiveis(
            @RequestParam(required = false) CategoriaAtendimento categoria,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(tipoService.disponiveis(medico.id(), categoria));
    }

    @GetMapping("/meus")
    @Operation(summary = "Lista os tipos do médico autenticado; busca pelo nome e filtra pela categoria")
    public ResponseEntity<PaginaResponse<TipoAtendimentoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @RequestParam(required = false) CategoriaAtendimento categoria,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(tipoService.listar(medico.id(), listagem, categoria));
    }

    @PostMapping
    @Operation(summary = "Cadastra um tipo do médico autenticado")
    public ResponseEntity<TipoAtendimentoResponse> cadastrar(
            @RequestBody @Valid TipoAtendimentoRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipoService.cadastrar(medico.id(), dados));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retorna um tipo do médico autenticado")
    public ResponseEntity<TipoAtendimentoResponse> buscar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(tipoService.buscar(medico.id(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um tipo do médico autenticado; é por aqui que ele é desativado")
    public ResponseEntity<TipoAtendimentoResponse> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid TipoAtendimentoRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(tipoService.atualizar(medico.id(), id, dados));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um tipo do médico autenticado; recusa se já houver exames ou consultas nele")
    public ResponseEntity<Response> excluir(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        tipoService.excluir(medico.id(), id);
        return ResponseEntity.ok(new Response("Tipo de atendimento excluído"));
    }
}
