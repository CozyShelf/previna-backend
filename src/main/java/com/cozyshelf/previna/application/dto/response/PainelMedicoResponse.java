package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;

import java.time.LocalDate;

// Painel da tela Início do médico, no alcance dele (as gestantes que acompanha, ou todas com verTodasGestantes).
// atrasados: AGENDADO com a data prevista antes de hoje; proximos7Dias: AGENDADO de amanhã até hoje + 7 (hoje fica
// na agenda do dia); semAgendamentoPendente: gestantes sem nenhum AGENDADO
public record PainelMedicoResponse(
        long gestantesAcompanhadas,
        ListaCurtaResponse<AgendamentoPendente> atrasados,
        ListaCurtaResponse<AgendamentoPendente> proximos7Dias,
        ListaCurtaResponse<GestanteSemPendente> semAgendamentoPendente
) {
    public record AgendamentoPendente(Long agendamentoId, Long gestanteId, String gestanteNome, String tipoNome,
                                      CategoriaAtendimento categoria, LocalDate dataPrevista) {}

    public record GestanteSemPendente(Long gestanteId, String gestanteNome) {}
}
