package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.MedicoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MedicoServiceTest {
    private static final String CPF = "52998224725";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MedicoService service = new MedicoService(usuarioRepository, medicoRepository, passwordEncoder);

    private final Medico medico = new Medico("Ana Souza", CPF, "ana@clinica.com", "CRM-SP 123456", "ana.souza",
            "hash", false);

    private static MedicoRequest dados(String login, String senha) {
        return dados(login, senha, false);
    }

    private static MedicoRequest dados(String login, String senha, boolean verTodasGestantes) {
        return new MedicoRequest("Ana Souza", CPF, "ana@clinica.com", "CRM-SP 123456", login, senha,
                verTodasGestantes);
    }

    @Test
    void cadastraComLoginESenhaDefinidosPeloAdministradorJaAtivo() {
        when(medicoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resposta = service.cadastrar(dados("ana.souza", "senhaForte1"));

        var salvo = ArgumentCaptor.forClass(Medico.class);
        verify(medicoRepository).save(salvo.capture());
        assertEquals("ana.souza", resposta.login());
        assertTrue(passwordEncoder.matches("senhaForte1", salvo.getValue().getSenhaHash()));
        assertFalse(salvo.getValue().primeiroAcessoPendente());
    }

    @Test
    void exigeSenhaNoCadastro() {
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(dados("ana.souza", null)));
        verify(medicoRepository, never()).save(any());
    }

    @Test
    void recusaCrmJaCadastrado() {
        when(medicoRepository.existsByCrm("CRM-SP 123456")).thenReturn(true);

        var erro = assertThrows(ConflitoException.class, () -> service.cadastrar(dados("ana.souza", "senhaForte1")));
        assertEquals("CRM já cadastrado", erro.getMessage());
        verify(medicoRepository, never()).save(any());
    }

    @Test
    void edicaoSemSenhaMantemASenhaENaoConfereOsProprios() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        // Os valores atuais existem no banco, mas são do próprio médico
        when(usuarioRepository.existsByCpf(CPF)).thenReturn(true);
        when(medicoRepository.existsByCrm("CRM-SP 123456")).thenReturn(true);
        when(usuarioRepository.existsByEmail("ana@clinica.com")).thenReturn(true);

        var resposta = service.atualizar(1L, dados("ana.s", null));

        assertEquals("ana.s", resposta.login());
        assertEquals("hash", medico.getSenhaHash());
    }

    @Test
    void edicaoComSenhaTrocaASenha() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));

        service.atualizar(1L, dados("ana.souza", "novaSenha2"));

        assertTrue(passwordEncoder.matches("novaSenha2", medico.getSenhaHash()));
    }

    @Test
    void administradorDaETiraAPermissaoDeVerTodasAsGestantes() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));

        assertTrue(service.atualizar(1L, dados("ana.souza", null, true)).verTodasGestantes());
        assertTrue(medico.podeVerTodasGestantes());

        service.atualizar(1L, dados("ana.souza", null, false));
        assertFalse(medico.podeVerTodasGestantes());
    }

    @Test
    void recusaLoginDeOutroUsuarioNaEdicao() {
        when(medicoRepository.findById(1L)).thenReturn(Optional.of(medico));
        when(usuarioRepository.existsByLogin("admin")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.atualizar(1L, dados("admin", null)));
        assertEquals("ana.souza", medico.getLogin());
    }

    @Test
    void medicoInexistente() {
        when(medicoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.buscar(9L));
    }
}
