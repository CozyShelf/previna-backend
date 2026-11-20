package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Cupom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CupomRepository extends JpaRepository<Cupom, Long>, JpaSpecificationExecutor<Cupom> {
    // Traz o produto na mesma consulta da página, em vez de uma por cupom
    @Override
    @EntityGraph(attributePaths = "produto")
    Page<Cupom> findAll(Specification<Cupom> filtro, Pageable pagina);

    Optional<Cupom> findByIdAndEmpresaId(Long id, Long empresaId);

    // SELECT ... FOR UPDATE para quem altera o cupom: editar e ativar/desativar ao mesmo tempo esperam um pelo outro.
    // Sem a trava, a edição gravava o ativo que leu por cima da desativação comitada no meio
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cupom c where c.id = :id and c.empresaId = :empresaId")
    Optional<Cupom> findByIdAndEmpresaIdParaAtualizar(@Param("id") Long id, @Param("empresaId") Long empresaId);

    // Cupons válidos (ativos e dentro da validade) dos produtos do catálogo de ofertas
    @Query("select c from Cupom c where c.produto.id in :produtos and c.ativo = true and c.dataValidade >= :dia")
    List<Cupom> validos(@Param("produtos") Collection<Long> produtos, @Param("dia") LocalDate dia, Sort ordem);
}
