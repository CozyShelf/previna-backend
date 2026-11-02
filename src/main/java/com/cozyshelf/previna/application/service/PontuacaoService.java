package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ExtratoRequest;
import com.cozyshelf.previna.application.dto.response.AtividadePontuadaResponse;
import com.cozyshelf.previna.application.dto.response.ExtratoResponse;
import com.cozyshelf.previna.application.dto.response.PontuacaoResponse;
import com.cozyshelf.previna.application.dto.response.TransacaoResponse;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.TransacaoPontosRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class PontuacaoService {
    // O id desempata transações no mesmo instante
    private static final Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("dataHora"), Sort.Order.desc("id"));

    private final TransacaoPontosRepository transacaoRepository;
    private final GestanteRepository gestanteRepository;

    public PontuacaoService(TransacaoPontosRepository transacaoRepository, GestanteRepository gestanteRepository) {
        this.transacaoRepository = transacaoRepository;
        this.gestanteRepository = gestanteRepository;
    }

    // O saldo é o da gestante, atualizado a cada crédito e débito, e não a soma das transações
    @Transactional(readOnly = true)
    public PontuacaoResponse consultarPontuacao(Long idGestante) {
        int saldo = gestanteRepository.findById(idGestante).orElseThrow().getSaldoPontos();
        List<AtividadePontuadaResponse> atividades = transacaoRepository
                .findByGestanteIdAndAgendamentoStatus(idGestante, StatusAgendamento.REALIZADO, MAIS_RECENTES).stream()
                .map(t -> new AtividadePontuadaResponse(t.getAgendamento().getId(),
                        t.getAgendamento().getTipoAtendimento().getNome(),
                        t.getAgendamento().getTipoAtendimento().getCategoria(), t.getPontos(), instante(t)))
                .toList();
        return new PontuacaoResponse(saldo, atividades);
    }

    // shortcut: o histórico vai inteiro, sem paginação (são dezenas de transações numa gestação); paginar se passar de centenas
    @Transactional(readOnly = true)
    public ExtratoResponse gerarExtrato(Long idGestante, ExtratoRequest filtros) {
        Specification<TransacaoPontos> filtro = Specification.allOf(
                Filtros.igual("gestanteId", idGestante),
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
