package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.EmpresaParceira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmpresaParceiraRepository extends JpaRepository<EmpresaParceira, Long>,
        JpaSpecificationExecutor<EmpresaParceira> {
    boolean existsByCnpj(String cnpj);
}
