package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PreCadastroGestanteRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.dto.response.CredenciaisTemporariasResponse;
import com.cozyshelf.previna.application.dto.response.GestanteResumoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PerfilGestanteResponse;
import com.cozyshelf.previna.application.dto.response.TokenResponse;
import com.cozyshelf.previna.application.service.AuthService;
import com.cozyshelf.previna.application.service.GestanteService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/gestantes")
@Tag(name = "Gestantes")
public class GestanteController {
    private final GestanteService gestanteService;
    private final AuthService authService;

    public GestanteController(GestanteService gestanteService, AuthService authService) {
        this.gestanteService = gestanteService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MEDICO')")
    @Operation(summary = "Lista as gestantes do médico autenticado, ou todas quando o administrador permitiu; busca por nome ou CPF")
    public ResponseEntity<PaginaResponse<GestanteResumoResponse>> listar(
            @ModelAttribute @Valid ListagemRequest listagem,
            @RequestParam(required = false) StatusConta situacao,
            @RequestParam(defaultValue = "false") boolean somenteMinhas,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.ok(gestanteService.listar(medico.id(), listagem, situacao, somenteMinhas));
    }

    @PostMapping("/pre-cadastro")
    @PreAuthorize("hasRole('MEDICO')")
    @Operation(summary = "Pré-cadastra uma gestante e retorna as credenciais temporárias")
    public ResponseEntity<CredenciaisTemporariasResponse> preCadastrar(
            @RequestBody @Valid PreCadastroGestanteRequest dados,
            @AuthenticationPrincipal UsuarioAutenticado medico) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gestanteService.preCadastrar(dados, medico.id()));
    }

    // Multipart para levar a foto (opcional) junto com os dados
    @PutMapping(value = "/primeiro-acesso", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PRIMEIRO_ACESSO')")
    @Operation(summary = "Troca as credenciais temporárias, registra os dados complementares e a foto opcional, ativa a conta e emite um novo token")
    public ResponseEntity<TokenResponse> concluirPrimeiroAcesso(
            @ModelAttribute @Valid PrimeiroAcessoRequest dados,
            @RequestParam(required = false) MultipartFile foto,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        gestanteService.concluirPrimeiroAcesso(gestante.id(), dados, foto);
        return ResponseEntity.ok(authService.renovarToken(gestante.id()));
    }

    @GetMapping("/perfil")
    @PreAuthorize("hasRole('GESTANTE')")
    @Operation(summary = "Retorna o perfil da gestante autenticada")
    public ResponseEntity<PerfilGestanteResponse> perfil(@AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(gestanteService.buscarPerfil(gestante.id()));
    }

    @PutMapping(value = "/perfil", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('GESTANTE')")
    @Operation(summary = "Atualiza o contato da gestante autenticada e troca ou remove a foto")
    public ResponseEntity<PerfilGestanteResponse> atualizarPerfil(
            @ModelAttribute @Valid AtualizacaoPerfilGestanteRequest dados,
            @RequestParam(required = false) MultipartFile foto,
            @AuthenticationPrincipal UsuarioAutenticado gestante) {
        return ResponseEntity.ok(gestanteService.atualizarPerfil(gestante.id(), dados, foto));
    }

    // Só a própria gestante vê a foto (dado pessoal); o app envia o token no cabeçalho da imagem
    @GetMapping("/foto")
    @PreAuthorize("hasRole('GESTANTE')")
    @Operation(summary = "Retorna a foto da gestante autenticada")
    public ResponseEntity<byte[]> foto(@AuthenticationPrincipal UsuarioAutenticado gestante) {
        return gestanteService.buscarFoto(gestante.id())
                .map(foto -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(foto.getTipo()))
                        .body(foto.getConteudo()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tutorial-concluido")
    @PreAuthorize("hasRole('GESTANTE')")
    @Operation(summary = "Registra que a gestante concluiu o tutorial de uso")
    public ResponseEntity<Void> concluirTutorial(@AuthenticationPrincipal UsuarioAutenticado gestante) {
        gestanteService.concluirTutorial(gestante.id());
        return ResponseEntity.noContent().build();
    }
}
