package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Promocao;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromocaoRepository extends JpaRepository<Promocao, Long>, JpaSpecificationExecutor<Promocao> {
    Optional<Promocao> findByIdAndEmpresaId(Long id, Long empresaId);

    // Promoções das empresas com o dia entre o início e o fim (os dois inclusive), para o catálogo de ofertas
    @Query("select p from Promocao p where p.empresaId in :empresas and p.dataInicio <= :dia and p.dataFim >= :dia")
    List<Promocao> vigentes(@Param("empresas") Collection<Long> empresas, @Param("dia") LocalDate dia, Sort ordem);
}
