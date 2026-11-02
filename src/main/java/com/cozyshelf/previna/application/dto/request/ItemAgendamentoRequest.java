package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Um exame ou consulta do cadastro. Sem limite superior de data: marcar com antecedência é decisão do
// médico; só o passado é recusado.
public record ItemAgendamentoRequest(
        @NotNull Long tipoAtendimento,
        @NotNull @FutureOrPresent LocalDate dataPrevista,
        @Size(max = 1000) String observacao
) {}
