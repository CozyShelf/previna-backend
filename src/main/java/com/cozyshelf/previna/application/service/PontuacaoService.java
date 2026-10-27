package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.DadosSocioeconomicos;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.TransacaoPontosRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Crédito de pontos pela realização de um exame ou consulta.
// Sem @Transactional próprio: roda dentro da transação de quem chama, para status, transação, saldo e extrato
// serem gravados juntos ou não serem gravados.
@Service
public class PontuacaoService {
    private final TransacaoPontosRepository transacaoRepository;
    private final DadosSocioeconomicosRepository dadosRepository;

    public PontuacaoService(TransacaoPontosRepository transacaoRepository,
                            DadosSocioeconomicosRepository dadosRepository) {
        this.transacaoRepository = transacaoRepository;
        this.dadosRepository = dadosRepository;
    }

    public int creditarPorAgendamento(Agendamento agendamento) {
        Gestante gestante = agendamento.getGestante();
        // Gestante sem dados socioeconômicos usa o fator neutro; o fator é 1,00 até a regra ser definida
        BigDecimal fator = dadosRepository.findById(gestante.getId())
                .map(DadosSocioeconomicos::getFatorPonderacao)
                .orElse(BigDecimal.ONE);
        int pontos = BigDecimal.valueOf(agendamento.getTipo().getPontosBase())
                .multiply(fator)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        // fator_aplicado guarda o fator usado, para o extrato continuar explicável se a regra mudar
        transacaoRepository.save(new TransacaoPontos(gestante, agendamento, TipoTransacao.CREDITO, pontos, fator));
        gestante.creditarPontos(pontos);
        return pontos;
    }
}
