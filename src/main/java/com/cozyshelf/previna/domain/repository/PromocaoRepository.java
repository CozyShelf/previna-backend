package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Promocao;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromocaoRepository extends JpaRepository<Promocao, Long>, JpaSpecificationExecutor<Promocao> {
    Optional<Promocao> findByIdAndEmpresaId(Long id, Long empresaId);

    // SELECT ... FOR UPDATE para quem altera a promoção, como no cupom: a edição não sobrescreve o ativo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promocao p where p.id = :id and p.empresaId = :empresaId")
    Optional<Promocao> findByIdAndEmpresaIdParaAtualizar(@Param("id") Long id, @Param("empresaId") Long empresaId);

    // Promoção vigente: ativa e com o dia entre o início e o fim (os dois inclusive). O mesmo critério do catálogo de
    // ofertas (Ofertas.promocaoVigente) e da Promocao.vigenteEm
    String VIGENTE = " p.ativo = true and p.dataInicio <= :dia and p.dataFim >= :dia";

    // Promoções vigentes das empresas, para o catálogo de ofertas
    @Query("select p from Promocao p where p.empresaId in :empresas and" + VIGENTE)
    List<Promocao> vigentes(@Param("empresas") Collection<Long> empresas, @Param("dia") LocalDate dia, Sort ordem);

    // Painel da empresa
    @Query("select count(p) from Promocao p where p.empresaId = :empresa and" + VIGENTE)
    long contarVigentes(@Param("empresa") Long idEmpresa, @Param("dia") LocalDate dia);
}
