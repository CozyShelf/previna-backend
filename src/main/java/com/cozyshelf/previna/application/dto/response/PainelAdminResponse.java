package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.AcaoAuditoria;
import com.cozyshelf.previna.domain.model.Perfil;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

// Painel da tela Início do admin: contagens globais, as empresas que ainda não fizeram o primeiro acesso e o log de
// auditoria recente. Nenhum dado de gestante: o admin não tem acesso a elas
public record PainelAdminResponse(
        long medicos,
        Contas empresas,
        Contas gestantes,
        Conteudos conteudos,
        ListaCurtaResponse<EmpresaPendente> empresasPendentes,
        List<Atividade> atividadeRecente
) {
    public record Contas(long total, long primeiroAcessoPendente) {}

    public record Conteudos(long publicados, long rascunhos) {}

    // preCadastradaEm: quando o admin pré-cadastrou a empresa (usuario.criado_em)
    public record EmpresaPendente(Long empresaId, String razaoSocial, Instant preCadastradaEm) {}

    // usuarioNome: nulo quando quem fez é a gestante (resgates); pontos: nulo quando a ação não mexe em pontos
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Atividade(Instant dataHora, AcaoAuditoria acao, String usuarioNome, Perfil perfil, Integer pontos) {}
}
