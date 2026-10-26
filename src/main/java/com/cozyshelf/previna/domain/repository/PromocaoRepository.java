package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PromocaoRepository extends JpaRepository<Promocao, Long>, JpaSpecificationExecutor<Promocao> {
}
