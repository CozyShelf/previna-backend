package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.StatusAgendamento;

import java.time.LocalDate;
import java.time.LocalDateTime;

// tipo é o nome do tipo de atendimento; dataConfirmacao só existe depois do desfecho
public record AgendamentoResponse(
        Long id,
        String tipo,
        CategoriaAtendimento categoria,
        Integer pontosBase,
        LocalDate dataPrevista,
        StatusAgendamento status,
        LocalDateTime dataConfirmacao,
        String observacao
) {}
