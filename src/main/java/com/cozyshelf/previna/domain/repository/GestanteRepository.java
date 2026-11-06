package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Gestante;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GestanteRepository extends JpaRepository<Gestante, Long>, JpaSpecificationExecutor<Gestante> {
    // Gestante de outro médico não volta na busca: para quem não tem a permissão, ela responde como inexistente
    Optional<Gestante> findByIdAndMedicoId(Long id, Long medicoId);

    // SELECT ... FOR UPDATE: dois resgates simultâneos da mesma gestante esperam um pelo outro e não passam do saldo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gestante g where g.id = :id")
    Optional<Gestante> findByIdParaAtualizar(@Param("id") Long id);

    // Traz o médico responsável na mesma consulta: a listagem mostra o nome dele
    @Override
    @EntityGraph(attributePaths = "medico")
    Page<Gestante> findAll(Specification<Gestante> filtro, Pageable pagina);
}
