package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.TipoAtendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TipoAtendimentoRepository
        extends JpaRepository<TipoAtendimento, Long>, JpaSpecificationExecutor<TipoAtendimento> {
    // O que o médico pode escolher num agendamento: os padrão mais os dele, só os ativos.
    // Sem filtro de categoria na consulta: o catálogo é de dezenas de linhas e quem filtra é o serviço.
    @Query("select t from TipoAtendimento t where t.ativo = true and (t.criadoPor is null or t.criadoPor = :idMedico)"
            + " order by t.nome")
    List<TipoAtendimento> disponiveisPara(@Param("idMedico") Long idMedico);

    // Tipo de outro médico, ou tipo padrão, responde como inexistente: ninguém edita o que não é seu
    Optional<TipoAtendimento> findByIdAndCriadoPor(Long id, Long criadoPor);

    boolean existsByNomeAndCriadoPor(String nome, Long criadoPor);
}
