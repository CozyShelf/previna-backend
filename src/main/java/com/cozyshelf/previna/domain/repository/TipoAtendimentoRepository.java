package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoAtendimentoRepository extends JpaRepository<TipoAtendimento, Long> {
    List<TipoAtendimento> findByAtivoTrueOrderByNomeAsc();

    List<TipoAtendimento> findByCategoriaAndAtivoTrueOrderByNomeAsc(CategoriaAtendimento categoria);
}
