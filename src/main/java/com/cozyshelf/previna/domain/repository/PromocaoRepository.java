package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PromocaoRepository extends JpaRepository<Promocao, Long>, JpaSpecificationExecutor<Promocao> {
    Optional<Promocao> findByIdAndEmpresaId(Long id, Long empresaId);
}
