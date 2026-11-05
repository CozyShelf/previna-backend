package com.cozyshelf.previna.application.dto.request;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Tipo de exame ou consulta cadastrado pelo médico. O teto de pontos existe porque a pontuação é moeda
// da gestante: um dígito a mais digitado por engano inflaria o saldo dela.
public record TipoAtendimentoRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotNull CategoriaAtendimento categoria,
        @NotNull @Min(1) @Max(1000) Integer pontosBase
) {}
