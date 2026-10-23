package com.cozyshelf.previna.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String remetente;
    private final String urlPortal;

    public EmailService(JavaMailSender mailSender,
                        @Value("${previna.email.remetente}") String remetente,
                        @Value("${previna.email.url-portal}") String urlPortal) {
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.urlPortal = urlPortal;
    }

    public void enviarPreLogin(String email, String loginTemporario, String senhaTemporaria) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(email);
        mensagem.setSubject("Seu acesso ao Previna");
        mensagem.setText("""
                Olá!

                Sua empresa foi pré-cadastrada como parceira do Previna.
                Entre no portal com os dados abaixo para criar seu login e senha definitivos e completar o cadastro.

                Portal: %s
                Login: %s
                Senha temporária: %s
                """.formatted(urlPortal, loginTemporario, senhaTemporaria));
        mailSender.send(mensagem);
    }
}
