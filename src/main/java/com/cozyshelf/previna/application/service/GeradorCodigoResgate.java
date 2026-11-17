package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// Código que a gestante mostra na loja, o mesmo para cupom e promoção (os dois ficam em cupom_resgatado)
@Component
public class GeradorCodigoResgate {
    // Sem 0/O, 1/I/L: o código é lido em voz alta ou digitado no caixa da loja
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 8;

    private final CupomResgatadoRepository cupomResgatadoRepository;
    private final SecureRandom aleatorio = new SecureRandom();

    public GeradorCodigoResgate(CupomResgatadoRepository cupomResgatadoRepository) {
        this.cupomResgatadoRepository = cupomResgatadoRepository;
    }

    // Confere antes de usar; o UNIQUE de cupom_resgatado.codigo é a última barreira se dois resgates sortearem o mesmo
    public String gerarCodigoUnico() {
        String codigo;
        do {
            StringBuilder sorteado = new StringBuilder(TAMANHO_CODIGO);
            for (int i = 0; i < TAMANHO_CODIGO; i++) {
                sorteado.append(ALFABETO_CODIGO.charAt(aleatorio.nextInt(ALFABETO_CODIGO.length())));
            }
            codigo = sorteado.toString();
        } while (cupomResgatadoRepository.existsByCodigo(codigo));
        return codigo;
    }
}
