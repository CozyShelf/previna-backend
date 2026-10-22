package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.PreCadastroEmpresaRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.infrastructure.email.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmpresaServiceTest {
    private static final String CNPJ = "11222333000181";

    private final EmpresaParceiraRepository empresaRepository = mock(EmpresaParceiraRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final EmpresaService service = new EmpresaService(empresaRepository, new CredencialService(),
            passwordEncoder, emailService);

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
}
