package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PreCadastroEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoEmpresaRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.LogoEmpresa;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.LogoEmpresaRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import com.cozyshelf.previna.infrastructure.email.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmpresaServiceTest {
    private static final String CNPJ = "11222333000181";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final EmpresaParceiraRepository empresaRepository = mock(EmpresaParceiraRepository.class);
    private final LogoEmpresaRepository logoRepository = mock(LogoEmpresaRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final EmpresaService service = new EmpresaService(usuarioRepository, empresaRepository,
            logoRepository, new CredencialService(), passwordEncoder, emailService);

    private final EmpresaParceira empresa = new EmpresaParceira("Farmácia Bem Nascer", CNPJ, "contato@bemnascer.com",
            "1133334444", "Ana Souza", CNPJ, "hash");

    private static PrimeiroAcessoEmpresaRequest primeiroAcesso(String login, String senha) {
        return new PrimeiroAcessoEmpresaRequest(login, senha, "Rua das Flores, 100", "Farmácia", null);
    }

    private static MockMultipartFile arquivo(String hex) {
        return new MockMultipartFile("logo", "logo", "image/png", HexFormat.of().parseHex(hex));
    }

    @Test
    void preCadastraComCnpjComoLoginEEnviaSenhaTemporariaPorEmail() {
        when(empresaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resumo = service.preCadastrar(new PreCadastroEmpresaRequest("Farmácia Bem Nascer", CNPJ,
                "contato@bemnascer.com", "1133334444", "Ana Souza"));

        var salva = ArgumentCaptor.forClass(EmpresaParceira.class);
        verify(empresaRepository).save(salva.capture());
        var senha = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarPreLogin(eq("contato@bemnascer.com"), eq(CNPJ), senha.capture());

        assertEquals(CNPJ, salva.getValue().getLogin());
        assertTrue(passwordEncoder.matches(senha.getValue(), salva.getValue().getSenhaHash()));
        assertTrue(resumo.primeiroAcessoPendente());
    }

    @Test
    void recusaCnpjJaCadastradoSemEnviarEmail() {
        when(empresaRepository.existsByCnpj(CNPJ)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.preCadastrar(new PreCadastroEmpresaRequest(
                "Outra Razão", CNPJ, "outro@email.com", "1133334444", "Outro")));
        verify(empresaRepository, never()).save(any());
        verify(emailService, never()).enviarPreLogin(anyString(), anyString(), anyString());
    }

    @Test
    void trocaCredenciaisCompletaCadastroEAtivaConta() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));

        service.concluirPrimeiroAcesso(1L, primeiroAcesso("bemnascer", "novaSenha1"), null);

        assertEquals("bemnascer", empresa.getLogin());
        assertTrue(passwordEncoder.matches("novaSenha1", empresa.getSenhaHash()));
        assertEquals("Farmácia", empresa.getSegmento());
        assertFalse(empresa.primeiroAcessoPendente());
    }

    @Test
    void recusaLoginEmUso() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByLogin("medico")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.concluirPrimeiroAcesso(1L, primeiroAcesso("medico", "novaSenha1"), null));
        assertTrue(empresa.primeiroAcessoPendente());
        assertNull(empresa.getSegmento());
    }

    @Test
    void recusaSegundoPrimeiroAcesso() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        service.concluirPrimeiroAcesso(1L, primeiroAcesso("bemnascer", "novaSenha1"), null);

        assertThrows(ConflitoException.class,
                () -> service.concluirPrimeiroAcesso(1L, primeiroAcesso("outro.login", "outraSenha2"), null));
    }

    @Test
    void guardaLogoEnviado() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));

        service.concluirPrimeiroAcesso(1L, primeiroAcesso("bemnascer", "novaSenha1"), arquivo("89504e470d0a1a0a"));

        var logo = ArgumentCaptor.forClass(LogoEmpresa.class);
        verify(logoRepository).save(logo.capture());
        assertEquals("image/png", logo.getValue().getTipo());
        assertTrue(empresa.getLogoUrl().startsWith("/empresas/1/logo?v="));
        assertFalse(empresa.primeiroAcessoPendente());
    }

    @Test
    void recusaArquivoQueNaoEImagemSemConcluirPrimeiroAcesso() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));

        // SVG declarado como PNG pelo navegador
        assertThrows(DadosInvalidosException.class, () -> service.concluirPrimeiroAcesso(1L,
                primeiroAcesso("bemnascer", "novaSenha1"), arquivo("3c73766720786d6c6e733d")));
        verify(logoRepository, never()).save(any());
        assertTrue(empresa.primeiroAcessoPendente());
    }

    @Test
    void atualizaContatoECadastroERemoveLogo() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        empresa.alterarLogo("/empresas/1/logo?v=1");

        var perfil = service.atualizarPerfil(1L, new AtualizacaoPerfilEmpresaRequest("novo@bemnascer.com",
                "11987654321", "Bia Lima", "Av. Brasil, 200", "Moda gestante", "Nova descrição", true), null);

        verify(logoRepository).deleteById(1L);
        assertNull(perfil.logoUrl());
        assertEquals("novo@bemnascer.com", perfil.email());
        assertEquals("Bia Lima", perfil.responsavel());
        assertEquals("Moda gestante", perfil.segmento());
    }

    @Test
    void recusaEmailDeOutroUsuario() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("medico@previna.com")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.atualizarPerfil(1L, new AtualizacaoPerfilEmpresaRequest(
                "medico@previna.com", "1133334444", "Ana Souza", "Rua A", "Farmácia", null, false), null));
        assertEquals("contato@bemnascer.com", empresa.getEmail());
    }
}
