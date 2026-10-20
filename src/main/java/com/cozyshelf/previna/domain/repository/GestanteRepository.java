package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.Gestante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GestanteRepository extends JpaRepository<Gestante, Long> {
    List<Gestante> findByMedicoIdOrderByNomeAsc(Long idMedico);
}
