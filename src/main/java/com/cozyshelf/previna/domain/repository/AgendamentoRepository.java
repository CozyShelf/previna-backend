package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // ---- Painel do médico (tela Início do portal) ----

    String AGENDADO = "com.cozyshelf.previna.domain.model.StatusAgendamento.AGENDADO";
    // Pendentes no alcance do médico: :medico nulo é o médico que vê todas as gestantes (a mesma regra da agenda do dia)
    String PENDENTES_NO_ALCANCE = " from Agendamento a where a.status = " + AGENDADO
            + " and (:medico is null or a.gestante.medico.id = :medico)";
    // Do mais antigo para o mais novo; sem horário na tabela, o id (ordem de cadastro) desempata
    String POR_DATA_PREVISTA = " order by a.dataPrevista, a.id";

    // Pendentes com a data prevista antes do dia (os atrasados, com o dia de hoje). A página traz os primeiros e o total
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    @Query(value = "select a" + PENDENTES_NO_ALCANCE + " and a.dataPrevista < :dia" + POR_DATA_PREVISTA,
            countQuery = "select count(a)" + PENDENTES_NO_ALCANCE + " and a.dataPrevista < :dia")
    Page<Agendamento> pendentesAntesDe(@Param("medico") Long idMedico, @Param("dia") LocalDate dia, Pageable pagina);

    // Pendentes com a data prevista entre os dois dias, inclusive
    @EntityGraph(attributePaths = {"tipo", "gestante"})
    @Query(value = "select a" + PENDENTES_NO_ALCANCE + " and a.dataPrevista between :inicio and :fim"
            + POR_DATA_PREVISTA,
            countQuery = "select count(a)" + PENDENTES_NO_ALCANCE + " and a.dataPrevista between :inicio and :fim")
    Page<Agendamento> pendentesEntre(@Param("medico") Long idMedico, @Param("inicio") LocalDate inicio,
                                     @Param("fim") LocalDate fim, Pageable pagina);
}
