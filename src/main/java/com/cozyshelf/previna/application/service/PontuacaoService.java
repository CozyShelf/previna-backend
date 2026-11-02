package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ExtratoRequest;
import com.cozyshelf.previna.application.dto.response.AtividadePontuadaResponse;
import com.cozyshelf.previna.application.dto.response.ExtratoResponse;
import com.cozyshelf.previna.application.dto.response.PontuacaoResponse;
import com.cozyshelf.previna.application.dto.response.TransacaoResponse;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.DadosSocioeconomicos;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.TransacaoPontosRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class PontuacaoService {
    // O id desempata transações no mesmo instante
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("dataHora"), Sort.Order.desc("id"));

    private final TransacaoPontosRepository transacaoRepository;
    private final DadosSocioeconomicosRepository dadosRepository;
    private final GestanteRepository gestanteRepository;

    public PontuacaoService(TransacaoPontosRepository transacaoRepository,
                            DadosSocioeconomicosRepository dadosRepository,
                            GestanteRepository gestanteRepository) {
        this.transacaoRepository = transacaoRepository;
        this.dadosRepository = dadosRepository;
        this.gestanteRepository = gestanteRepository;
    }

    // Crédito pela realização de um exame ou consulta.
    // Sem @Transactional próprio: roda dentro da transação de quem chama, para status, transação, saldo e extrato
    // serem gravados juntos ou não serem gravados.
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

    // O saldo é o da gestante, atualizado a cada crédito e débito, e não a soma das transações
    @Transactional(readOnly = true)
    public PontuacaoResponse consultarPontuacao(Long idGestante) {
        int saldo = gestanteRepository.findById(idGestante).orElseThrow().getSaldoPontos();
        List<AtividadePontuadaResponse> atividades = transacaoRepository
                .findByGestanteIdAndAgendamentoStatus(idGestante, StatusAgendamento.REALIZADO, MAIS_RECENTES).stream()
                .map(t -> new AtividadePontuadaResponse(t.getAgendamento().getId(),
                        t.getAgendamento().getTipo().getNome(),
                        t.getAgendamento().getTipo().getCategoria(), t.getPontos(), instante(t)))
                .toList();
        return new PontuacaoResponse(saldo, atividades);
    }

    // shortcut: o histórico vai inteiro, sem paginação (são dezenas de transações numa gestação); paginar se passar de centenas
    @Transactional(readOnly = true)
    public ExtratoResponse gerarExtrato(Long idGestante, ExtratoRequest filtros) {
        Specification<TransacaoPontos> filtro = Specification.allOf(
                Filtros.igual("gestante", gestanteRepository.getReferenceById(idGestante)),
                Filtros.igual("tipo", filtros.tipo()),
                Filtros.noPeriodo("dataHora", filtros.inicio(), filtros.fim()));
        List<TransacaoPontos> transacoes = transacaoRepository.findAll(filtro, MAIS_RECENTES);
        return new ExtratoResponse(total(transacoes, TipoTransacao.CREDITO), total(transacoes, TipoTransacao.DEBITO),
                transacoes.stream()
                        .map(t -> new TransacaoResponse(t.getId(), t.getTipo(), t.getPontos(), t.getDescricao(),
                                instante(t)))
                        .toList());
    }

    // A data e hora gravada está no fuso da JVM; o app recebe o instante para mostrar no fuso do celular
    private static Instant instante(TransacaoPontos transacao) {
        return transacao.getDataHora().atZone(ZoneId.systemDefault()).toInstant();
    }

    private static int total(List<TransacaoPontos> transacoes, TipoTransacao tipo) {
        return transacoes.stream().filter(t -> t.getTipo() == tipo).mapToInt(TransacaoPontos::getPontos).sum();
    }
}
