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

    // Cupom válido: ativo e com a validade não vencida no dia (vale até o fim do dia da validade). O mesmo critério do
    // catálogo de ofertas (Ofertas.cupomValido) e do Cupom.disponivelEm
    String VALIDO = " c.ativo = true and c.dataValidade >= :dia";

    // Cupons válidos dos produtos do catálogo de ofertas
    @Query("select c from Cupom c where c.produto.id in :produtos and" + VALIDO)
    List<Cupom> validos(@Param("produtos") Collection<Long> produtos, @Param("dia") LocalDate dia, Sort ordem);

    // ---- Painel da empresa ----

    String VALIDOS_DA_EMPRESA = " from Cupom c where c.empresaId = :empresa and" + VALIDO;

    @Query("select count(c)" + VALIDOS_DA_EMPRESA)
    long contarValidos(@Param("empresa") Long idEmpresa, @Param("dia") LocalDate dia);

    // Válidos que vencem até o dia "ate", do que vence primeiro; o id desempata. Com o produto, para o nome
    @EntityGraph(attributePaths = "produto")
    @Query(value = "select c" + VALIDOS_DA_EMPRESA + " and c.dataValidade <= :ate order by c.dataValidade, c.id",
            countQuery = "select count(c)" + VALIDOS_DA_EMPRESA + " and c.dataValidade <= :ate")
    Page<Cupom> validosVencendoAte(@Param("empresa") Long idEmpresa, @Param("dia") LocalDate dia,
                                   @Param("ate") LocalDate ate, Pageable pagina);
}
