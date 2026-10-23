package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MedicoRepository extends JpaRepository<Medico, Long>, JpaSpecificationExecutor<Medico> {
    boolean existsByCrm(String crm);
}
