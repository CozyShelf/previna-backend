package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.EmpresaParceira;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpresaParceiraRepository extends JpaRepository<EmpresaParceira, Long> {
    boolean existsByCnpj(String cnpj);

    List<EmpresaParceira> findAllByOrderByRazaoSocialAsc();
}
