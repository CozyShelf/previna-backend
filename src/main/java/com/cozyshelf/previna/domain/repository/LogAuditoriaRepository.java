package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.LogAuditoria;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// Inserção e uma leitura só, a do painel do admin: estende Repository, e não JpaRepository, para não expor delete.
// O gatilho da V24 barra UPDATE e DELETE no banco de qualquer forma
public interface LogAuditoriaRepository extends Repository<LogAuditoria, Long> {
    LogAuditoria save(LogAuditoria registro);

    List<LogAuditoria> saveAll(Iterable<LogAuditoria> registros);

    // As últimas linhas, da mais recente para a mais antiga, com quem fez. A ordem é a do id (a de inserção), que usa a
    // chave primária; ordenar por data_hora leria a tabela inteira, sem índice para isso.
    // Nenhum dado da gestante sai daqui: nem gestante_id, nem o nome dela quando foi ela quem fez (resgates)
    @Query("select new com.cozyshelf.previna.domain.repository.AtividadeDoLog(l.dataHora, l.acao,"
            + " case when u.perfil = com.cozyshelf.previna.domain.model.Perfil.GESTANTE then null else u.nome end,"
            + " u.perfil, l.pontos)"
            + " from LogAuditoria l join Usuario u on u.id = l.usuarioId order by l.id desc")
    List<AtividadeDoLog> recentes(Limit limite);
}
