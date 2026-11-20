package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.LogAuditoria;
import org.springframework.data.repository.Repository;

import java.util.List;

// Só inserção: estende Repository, e não JpaRepository, para não expor delete, nem leitura (não há tela do log).
// O gatilho da V24 barra UPDATE e DELETE no banco de qualquer forma
public interface LogAuditoriaRepository extends Repository<LogAuditoria, Long> {
    LogAuditoria save(LogAuditoria registro);

    List<LogAuditoria> saveAll(Iterable<LogAuditoria> registros);
}
