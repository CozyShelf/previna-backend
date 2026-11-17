package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.CupomResgatado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CupomResgatadoRepository extends JpaRepository<CupomResgatado, Long> {
    boolean existsByCodigo(String codigo);

    // Resgate já feito com a mesma chave de idempotência: o toque repetido no app
    Optional<CupomResgatado> findByGestanteIdAndChaveIdempotencia(Long gestanteId, UUID chaveIdempotencia);
}
