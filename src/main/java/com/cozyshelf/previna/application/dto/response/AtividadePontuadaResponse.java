package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;

import java.time.Instant;

// Consulta ou exame confirmado pelo médico; dataHora é quando os pontos foram creditados
public record AtividadePontuadaResponse(
        Long agendamento,
        String nome,
        CategoriaAtendimento categoria,
        int pontos,
        Instant dataHora
) {}
