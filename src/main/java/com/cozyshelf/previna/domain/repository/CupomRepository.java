package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Cupom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CupomRepository extends JpaRepository<Cupom, Long>, JpaSpecificationExecutor<Cupom> {
    // Traz o produto na mesma consulta da página, em vez de uma por cupom
    @Override
    @EntityGraph(attributePaths = "produto")
    Page<Cupom> findAll(Specification<Cupom> filtro, Pageable pagina);

    Optional<Cupom> findByIdAndEmpresaId(Long id, Long empresaId);

    // Cupons que a gestante pode resgatar: ativos e dentro da validade, de todas as empresas
    // shortcut: a lista vai inteira, sem paginação (dezenas de cupons); paginar se passar de centenas
    @EntityGraph(attributePaths = "produto")
    List<Cupom> findByAtivoTrueAndDataValidadeGreaterThanEqual(LocalDate dia, Sort ordem);
}
