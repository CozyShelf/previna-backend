package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {
    Optional<Produto> findByIdAndEmpresaId(Long id, Long empresaId);
}
