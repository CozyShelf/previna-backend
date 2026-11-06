package com.cozyshelf.previna.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService service = new EmailService(mailSender, "Previna <nao-responda@previna.com.br>",
            "https://portal.previna.com.br");

    @Test
    void enviaPreLoginComPortalLoginESenhaTemporaria() {
        service.enviarPreLogin("contato@vida.com", "11222333000181", "Tmp#4821xk");

        var enviada = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(enviada.capture());
        SimpleMailMessage mensagem = enviada.getValue();
        assertEquals("Previna <nao-responda@previna.com.br>", mensagem.getFrom());
        assertArrayEquals(new String[]{"contato@vida.com"}, mensagem.getTo());
        assertEquals("Seu acesso ao Previna", mensagem.getSubject());
        assertTrue(mensagem.getText().contains("Portal: https://portal.previna.com.br"));
        assertTrue(mensagem.getText().contains("Login: 11222333000181"));
        assertTrue(mensagem.getText().contains("Senha temporária: Tmp#4821xk"));
    }

    // A falha do SMTP sobe: é ela que desfaz o pré-cadastro da empresa (ver EmpresaService.preCadastrar)
    @Test
    void falhaNoEnvioSobeParaQuemChamou() {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class,
                () -> service.enviarPreLogin("contato@vida.com", "11222333000181", "Tmp#4821xk"));
    }
}
