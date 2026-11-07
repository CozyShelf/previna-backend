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
    // O que o médico pode escolher num agendamento: os padrão mais os dele, sem os excluídos.
    // Sem filtro de categoria na consulta: o catálogo é de dezenas de linhas e quem filtra é o serviço.
    @Query("select t from TipoAtendimento t where t.ativo = true and (t.criadoPor is null or t.criadoPor = :idMedico)"
            + " order by t.nome")
    List<TipoAtendimento> disponiveisPara(@Param("idMedico") Long idMedico);

    // Tipo de outro médico, tipo padrão ou já excluído responde como inexistente: ninguém edita o que não é seu
    Optional<TipoAtendimento> findByIdAndCriadoPorAndAtivoTrue(Long id, Long criadoPor);

    // O nome só é disputado entre os tipos vivos; o índice único da V14 vale com a mesma condição
    boolean existsByNomeAndCriadoPorAndAtivoTrue(String nome, Long criadoPor);
}
