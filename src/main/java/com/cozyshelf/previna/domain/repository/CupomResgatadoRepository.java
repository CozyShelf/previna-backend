package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.CupomResgatado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CupomResgatadoRepository extends JpaRepository<CupomResgatado, Long> {
    boolean existsByCodigo(String codigo);
}
