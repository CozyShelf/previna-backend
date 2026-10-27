package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Agendamento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {
    // Traz o tipo e a gestante na mesma consulta: a listagem mostra o nome do tipo e a confirmação
    // precisa da gestante para creditar os pontos
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    List<Agendamento> findByGestanteId(Long gestanteId);

    @Override
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    Optional<Agendamento> findById(Long id);
}
