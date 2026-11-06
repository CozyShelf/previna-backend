package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.LoginRequest;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// JwtService e BCrypt de verdade: o token emitido é validado de volta para conferir o papel que ele carrega
class AuthServiceTest {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService("segredo-de-teste-com-no-minimo-32-bytes!!", 60, 30);
    private final AuthService service = new AuthService(usuarioRepository, passwordEncoder, jwtService);

    private final String senhaHash = passwordEncoder.encode("senhaForte1");

    private Medico medico(boolean verTodasGestantes) {
        return Campos.definir(new Medico("Ana Souza", "52998224725", "ana@clinica.com", "CRM-SP 1", "ana.souza",
                senhaHash, verTodasGestantes), "id", 2L);
    }

    private Gestante gestante() {
        return Campos.definir(new Gestante("Maria", "12345678909", "maria@exemplo.com", "12345678909", senhaHash,
                null, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()), "id", 7L);
    }

    private EmpresaParceira empresa() {
        var empresa = Campos.definir(new EmpresaParceira("Farmácia Vida", "11222333000181", "contato@vida.com",
                "1133334444", "Carla", "farmacia.vida", senhaHash), "id", 3L);
        empresa.alterarLogo("/empresas/3/logo?v=1");
        return Campos.definir(empresa, "statusConta", StatusConta.ATIVA);
    }

    // ---- Login ----

    @Test
    void loginComSenhaCertaEmiteTokenComOPerfil() {
        when(usuarioRepository.findByLogin("ana.souza")).thenReturn(Optional.of(medico(false)));

        var resposta = service.autenticar(new LoginRequest("ana.souza", "senhaForte1"));

        var usuario = jwtService.validarToken(resposta.token());
        assertEquals(2L, usuario.id());
        assertEquals("MEDICO", usuario.papel());
        assertFalse(resposta.primeiroAcesso());
        assertFalse(resposta.tutorialPendente());
    }

    // Senha temporária: o token só serve para concluir o primeiro acesso, e o app sabe que deve mostrar o tutorial
    @Test
    void loginDeGestanteNoPrimeiroAcessoEmiteTokenTemporario() {
        when(usuarioRepository.findByLogin("12345678909")).thenReturn(Optional.of(gestante()));

        var resposta = service.autenticar(new LoginRequest("12345678909", "senhaForte1"));

        assertEquals(JwtService.PAPEL_PRIMEIRO_ACESSO_GESTANTE, jwtService.validarToken(resposta.token()).papel());
        assertTrue(resposta.primeiroAcesso());
        assertTrue(resposta.tutorialPendente());
    }

    // Senha errada e login inexistente dão a mesma resposta: nada revela quais logins existem
    @Test
    void senhaErradaELoginInexistenteRespondemIgual() {
        when(usuarioRepository.findByLogin("ana.souza")).thenReturn(Optional.of(medico(false)));
        when(usuarioRepository.findByLogin("ninguem")).thenReturn(Optional.empty());

        var senhaErrada = assertThrows(BadCredentialsException.class,
                () -> service.autenticar(new LoginRequest("ana.souza", "outraSenha1")));
        var inexistente = assertThrows(BadCredentialsException.class,
                () -> service.autenticar(new LoginRequest("ninguem", "senhaForte1")));

        assertEquals("Login ou senha inválidos", senhaErrada.getMessage());
        assertEquals(senhaErrada.getMessage(), inexistente.getMessage());
    }

    // ---- Renovação depois do primeiro acesso ----

    @Test
    void renovaTokenComOPapelDefinitivoDepoisDoPrimeiroAcesso() {
        var gestante = gestante();
        gestante.concluirPrimeiroAcesso("maria.silva", senhaHash);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(gestante));

        var resposta = service.renovarToken(7L);

        assertEquals("GESTANTE", jwtService.validarToken(resposta.token()).papel());
        assertFalse(resposta.primeiroAcesso());
        // O tutorial é outra etapa: continua pendente até a gestante concluí-lo
        assertTrue(resposta.tutorialPendente());
    }

    @Test
    void renovacaoDeUsuarioInexistenteFalha() {
        when(usuarioRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.renovarToken(99L));
    }

    // ---- Perfil: cada papel só recebe os campos que fazem sentido para ele ----

    @Test
    void perfilDoMedicoTrazCrmEPermissao() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico(true)));

        var perfil = service.buscarPerfil(2L);

        assertEquals("Ana Souza", perfil.nome());
        assertEquals("ana.souza", perfil.login());
        assertEquals("CRM-SP 1", perfil.crm());
        assertTrue(perfil.verTodasGestantes());
        assertNull(perfil.cnpj());
        assertNull(perfil.logoUrl());
    }

    @Test
    void perfilDaEmpresaTrazCnpjELogo() {
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(empresa()));

        var perfil = service.buscarPerfil(3L);

        assertEquals("Farmácia Vida", perfil.nome());
        assertEquals("contato@vida.com", perfil.email());
        assertEquals("11222333000181", perfil.cnpj());
        assertEquals("/empresas/3/logo?v=1", perfil.logoUrl());
        assertNull(perfil.crm());
        assertNull(perfil.verTodasGestantes());
    }

    @Test
    void perfilDaGestanteSoTemOsDadosComuns() {
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(gestante()));

        var perfil = service.buscarPerfil(7L);

        assertEquals("Maria", perfil.nome());
        assertEquals("maria@exemplo.com", perfil.email());
        assertNull(perfil.crm());
        assertNull(perfil.cnpj());
        assertNull(perfil.logoUrl());
        assertNull(perfil.verTodasGestantes());
    }

    @Test
    void perfilDoAdministradorSoTemOsDadosComuns() throws Exception {
        // O administrador é um Usuario sem subclasse; o construtor é protegido porque só o banco o cria
        Constructor<Usuario> construtor = Usuario.class.getDeclaredConstructor();
        construtor.setAccessible(true);
        Usuario admin = Campos.definir(Campos.definir(construtor.newInstance(), "nome", "Administração"),
                "perfil", Perfil.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        var perfil = service.buscarPerfil(1L);

        assertEquals("Administração", perfil.nome());
        assertNull(perfil.crm());
        assertNull(perfil.cnpj());
        assertNull(perfil.verTodasGestantes());
    }
}
