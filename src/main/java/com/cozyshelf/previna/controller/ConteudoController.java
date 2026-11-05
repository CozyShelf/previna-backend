package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ConteudoRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PublicacaoConteudoRequest;
import com.cozyshelf.previna.application.dto.response.ConteudoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.Response;
import com.cozyshelf.previna.application.service.ConteudoService;
import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Conteúdo educativo: o admin cadastra e publica, a gestante lê no app. As duas leituras usam a mesma
// rota (como nos diagramas de sequência); quem é admin vê também os rascunhos, a gestante só o publicado.
@RestController
@RequestMapping("/conteudos")
@Tag(name = "Conteúdos educativos")
public class ConteudoController {
    private final ConteudoService conteudoService;

    public ConteudoController(ConteudoService conteudoService) {
        this.conteudoService = conteudoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTANTE')")
    @Operation(summary = "Admin: lista todos os conteúdos, com busca pelo título e filtros. "
            + "Gestante: só os publicados, em ordem aleatória semeada por 'semente'")
    public ResponseEntity<PaginaResponse<ConteudoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @RequestParam(required = false) CategoriaConteudo categoria,
            @RequestParam(required = false) TipoConteudo tipo,
            @RequestParam(required = false) Boolean publicado,
            @RequestParam(required = false) String semente,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(ehAdmin(usuario)
                ? conteudoService.listarParaGestao(listagem, categoria, tipo, publicado)
                : conteudoService.listarPublicados(listagem, semente));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTANTE')")
    @Operation(summary = "Retorna um conteúdo; para a gestante, um rascunho responde como inexistente")
    public ResponseEntity<ConteudoResponse> buscar(@PathVariable Long id,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(ehAdmin(usuario)
                ? conteudoService.buscar(id)
                : conteudoService.buscarPublicado(id));
    }

    // Multipart para levar a imagem junto com os dados
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastra um conteúdo como rascunho, com a imagem opcional")
    public ResponseEntity<ConteudoResponse> cadastrar(
            @ModelAttribute @Valid ConteudoRequest dados,
            @RequestParam(required = false) MultipartFile imagem,
            @AuthenticationPrincipal UsuarioAutenticado admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conteudoService.cadastrar(admin.id(), dados, imagem));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza um conteúdo; troca a imagem quando uma nova é enviada")
    public ResponseEntity<ConteudoResponse> atualizar(
            @PathVariable Long id,
            @ModelAttribute @Valid ConteudoRequest dados,
            @RequestParam(required = false) MultipartFile imagem) {
        return ResponseEntity.ok(conteudoService.atualizar(id, dados, imagem));
    }

    @PutMapping("/{id}/publicado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publica ou despublica um conteúdo; despublicar o tira do app sem apagá-lo")
    public ResponseEntity<ConteudoResponse> alterarPublicacao(
            @PathVariable Long id,
            @RequestBody @Valid PublicacaoConteudoRequest dados) {
        return ResponseEntity.ok(conteudoService.alterarPublicacao(id, dados.publicado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exclui um conteúdo e a imagem dele")
    public ResponseEntity<Response> excluir(@PathVariable Long id) {
        conteudoService.excluir(id);
        return ResponseEntity.ok(new Response("Conteúdo excluído"));
    }

    @GetMapping("/{id}/imagem")
    @Operation(summary = "Retorna a imagem do conteúdo")
    public ResponseEntity<byte[]> imagem(@PathVariable Long id) {
        return conteudoService.buscarImagem(id)
                .map(imagem -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(imagem.getTipo()))
                        .body(imagem.getConteudo()))
                .orElse(ResponseEntity.notFound().build());
    }

    private static boolean ehAdmin(UsuarioAutenticado usuario) {
        return Perfil.ADMIN.name().equals(usuario.papel());
    }
}
