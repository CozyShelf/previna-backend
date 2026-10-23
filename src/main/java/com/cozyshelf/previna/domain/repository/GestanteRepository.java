package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Gestante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GestanteRepository extends JpaRepository<Gestante, Long>, JpaSpecificationExecutor<Gestante> {
    // Traz o médico responsável na mesma consulta: a listagem mostra o nome dele
    @Override
    @EntityGraph(attributePaths = "medico")
    Page<Gestante> findAll(Specification<Gestante> filtro, Pageable pagina);
}
