package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PreCadastroEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoEmpresaRequest;
import com.cozyshelf.previna.application.dto.response.EmpresaResumoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PerfilEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.TokenResponse;
import com.cozyshelf.previna.application.service.AuthService;
import com.cozyshelf.previna.application.service.EmpresaService;
import com.cozyshelf.previna.domain.model.StatusConta;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/empresas")
@Tag(name = "Empresas parceiras")
public class EmpresaController {
    private final EmpresaService empresaService;
    private final AuthService authService;

    public EmpresaController(EmpresaService empresaService, AuthService authService) {
        this.empresaService = empresaService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista as empresas parceiras; busca por razão social, CNPJ, responsável ou e-mail")
    public ResponseEntity<PaginaResponse<EmpresaResumoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @RequestParam(required = false) StatusConta situacao) {
        return ResponseEntity.ok(empresaService.listar(listagem, situacao));
    }

    @PostMapping("/pre-cadastro")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Pré-cadastra uma empresa parceira e envia as credenciais temporárias por e-mail")
    public ResponseEntity<EmpresaResumoResponse> preCadastrar(@RequestBody @Valid PreCadastroEmpresaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaService.preCadastrar(dados));
    }

    // Multipart para levar a imagem do logo junto com os dados
    @PutMapping(value = "/primeiro-acesso", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PRIMEIRO_ACESSO')")
    @Operation(summary = "Troca as credenciais temporárias, completa o cadastro (com o logo opcional), ativa a conta e emite um novo token")
    public ResponseEntity<TokenResponse> concluirPrimeiroAcesso(
            @ModelAttribute @Valid PrimeiroAcessoEmpresaRequest dados,
            @RequestParam(required = false) MultipartFile logo,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        empresaService.concluirPrimeiroAcesso(empresa.id(), dados, logo);
        return ResponseEntity.ok(authService.renovarToken(empresa.id()));
    }

    @GetMapping("/perfil")
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Retorna os dados editáveis da empresa autenticada")
    public ResponseEntity<PerfilEmpresaResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(empresaService.buscarPerfil(empresa.id()));
    }

    @PutMapping(value = "/perfil", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPRESA_PARCEIRA')")
    @Operation(summary = "Atualiza os dados da empresa autenticada e troca ou remove o logo")
    public ResponseEntity<PerfilEmpresaResponse> atualizarPerfil(
            @ModelAttribute @Valid AtualizacaoPerfilEmpresaRequest dados,
            @RequestParam(required = false) MultipartFile logo,
            @AuthenticationPrincipal UsuarioAutenticado empresa) {
        return ResponseEntity.ok(empresaService.atualizarPerfil(empresa.id(), dados, logo));
    }

    @GetMapping("/{id}/logo")
    @Operation(summary = "Retorna a imagem do logo da empresa parceira")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        return empresaService.buscarLogo(id)
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.getTipo()))
                        .body(logo.getConteudo()))
                .orElse(ResponseEntity.notFound().build());
    }
}
