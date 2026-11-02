package com.cozyshelf.previna.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Um cadastro marca vários exames e consultas de uma vez para a mesma gestante, cada um com o seu tipo e a
// sua data. Cada item vira um agendamento independente. O teto por envio limita o tamanho da requisição;
// o médico que precisar de mais faz um segundo cadastro.
public record AgendamentoRequest(
        @NotNull Long gestante,
        @NotEmpty @Size(max = 20) @Valid List<ItemAgendamentoRequest> itens
) {}
