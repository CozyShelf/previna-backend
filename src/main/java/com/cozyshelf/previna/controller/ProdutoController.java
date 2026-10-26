package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.ProdutoRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoResponse;
import com.cozyshelf.previna.application.service.ProdutoService;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// A empresa vem do token, não do caminho: uma empresa não cadastra produtos em nome de outra
@RestController
@RequestMapping("/empresas/produtos")
@Tag(name = "Produtos")
public class ProdutoController {
    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Lista os produtos da empresa autenticada; busca pelo nome e filtra pela categoria")
    public ResponseEntity<PaginaResponse<ProdutoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @RequestParam(required = false) Categoria categoria,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(produtoService.listar(empresa.id(), listagem, categoria));
    }

    // Multipart para levar a imagem junto com os dados
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Cadastra um produto da empresa autenticada, com a imagem opcional")
    public ResponseEntity<ProdutoResponse> cadastrar(
            @ModelAttribute @Valid ProdutoRequest dados,
            @RequestParam(required = false) MultipartFile imagem,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoService.cadastrar(empresa.id(), dados, imagem));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Retorna um produto da empresa autenticada")
    public ResponseEntity<ProdutoResponse> buscar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(produtoService.buscar(empresa.id(), id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Atualiza um produto da empresa autenticada; troca a imagem quando uma nova é enviada")
    public ResponseEntity<ProdutoResponse> atualizar(
            @PathVariable Long id,
            @ModelAttribute @Valid ProdutoRequest dados,
            @RequestParam(required = false) MultipartFile imagem,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(produtoService.atualizar(empresa.id(), id, dados, imagem));
    }

    @GetMapping("/{id}/imagem")
    @Operation(summary = "Retorna a imagem do produto")
    public ResponseEntity<byte[]> imagem(@PathVariable Long id) {
        return produtoService.buscarImagem(id)
                .map(imagem -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(imagem.getTipo()))
                        .body(imagem.getConteudo()))
                .orElse(ResponseEntity.notFound().build());
    }
}
