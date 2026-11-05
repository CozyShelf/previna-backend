package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {
    // Traz o tipo e a gestante na mesma consulta: a listagem mostra o nome do tipo e a confirmação
    // precisa da gestante para creditar os pontos
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    List<Agendamento> findByGestanteId(Long gestanteId);

    // Pendentes do app: o que o médico ainda não resolveu
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    List<Agendamento> findByGestanteIdAndStatus(Long gestanteId, StatusAgendamento status);

    // Histórico do app: tudo o que saiu do AGENDADO, realizado ou não comparecido
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    List<Agendamento> findByGestanteIdAndStatusNot(Long gestanteId, StatusAgendamento status);

    @Override
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    Optional<Agendamento> findById(Long id);

    // Trava a linha até o fim da transação: confirmar e registrar não comparecimento ao mesmo tempo liam o
    // mesmo AGENDADO e um sobrescrevia o outro, deixando um não comparecimento com pontos creditados.
    // Sem @EntityGraph: SELECT ... FOR UPDATE com join fetch não é aceito em todos os bancos; tipo e gestante
    // carregam sob demanda, ainda dentro da transação.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agendamento a where a.id = :id")
    Optional<Agendamento> findParaResolver(@Param("id") Long id);

    // Agenda do dia do médico que vê todas as gestantes. Sem horário na tabela, o id é a ordem de cadastro.
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    List<Agendamento> findByStatusAndDataPrevistaOrderByIdAsc(StatusAgendamento status, LocalDate dia);

    // Agenda do dia restrita às gestantes que o médico acompanha
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    List<Agendamento> findByStatusAndDataPrevistaAndGestanteMedicoIdOrderByIdAsc(
            StatusAgendamento status, LocalDate dia, Long idMedico);
}
