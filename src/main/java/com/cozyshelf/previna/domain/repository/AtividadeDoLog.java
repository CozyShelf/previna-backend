package com.cozyshelf.previna.domain.repository;

import com.cozyshelf.previna.domain.model.AcaoAuditoria;
import com.cozyshelf.previna.domain.model.Perfil;

import java.time.LocalDateTime;

// Linha do log de auditoria lida pelo painel do admin, com o nome e o perfil de quem fez, na mesma consulta.
// usuarioNome vem nulo quando quem fez é a gestante: o admin não vê dados de gestantes
public record AtividadeDoLog(LocalDateTime dataHora, AcaoAuditoria acao, String usuarioNome, Perfil perfil,
                             Integer pontos) {}
