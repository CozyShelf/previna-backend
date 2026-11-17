package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.CupomResgatado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CupomResgatadoRepository extends JpaRepository<CupomResgatado, Long> {
    boolean existsByCodigo(String codigo);

    // Resgate já feito com a mesma chave de idempotência: o toque repetido no app
    Optional<CupomResgatado> findByGestanteIdAndChaveIdempotencia(Long gestanteId, UUID chaveIdempotencia);

    // Quais destas promoções a gestante já resgatou, numa consulta só para todas
    @Query("select r.promocao.id from CupomResgatado r where r.gestante.id = :gestante and r.promocao.id in :promocoes")
    Set<Long> promocoesResgatadas(@Param("gestante") Long idGestante, @Param("promocoes") Collection<Long> promocoes);
}
