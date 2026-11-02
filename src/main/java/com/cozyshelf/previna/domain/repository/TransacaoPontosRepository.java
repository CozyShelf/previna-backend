package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

// Traz o agendamento e o tipo de atendimento na mesma consulta, em vez de uma por transação: a descrição vem deles
public interface TransacaoPontosRepository extends JpaRepository<TransacaoPontos, Long>,
        JpaSpecificationExecutor<TransacaoPontos> {
    @Override
    @EntityGraph(attributePaths = "agendamento.tipo")
    List<TransacaoPontos> findAll(Specification<TransacaoPontos> filtro, Sort ordem);

    @EntityGraph(attributePaths = "agendamento.tipo")
    List<TransacaoPontos> findByGestanteIdAndAgendamentoStatus(Long gestanteId, StatusAgendamento status, Sort ordem);
}
