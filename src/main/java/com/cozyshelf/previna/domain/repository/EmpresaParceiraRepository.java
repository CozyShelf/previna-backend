package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.StatusConta;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmpresaParceiraRepository extends JpaRepository<EmpresaParceira, Long>,
        JpaSpecificationExecutor<EmpresaParceira> {
    boolean existsByCnpj(String cnpj);

    // SELECT ... FOR UPDATE: duas conclusões simultâneas do primeiro acesso esperam uma pela outra
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmpresaParceira e where e.id = :id")
    Optional<EmpresaParceira> findByIdParaAtualizar(@Param("id") Long id);

    // ---- Painel do admin ----

    long countByStatusConta(StatusConta statusConta);

    Page<EmpresaParceira> findByStatusConta(StatusConta statusConta, Pageable pagina);
}
