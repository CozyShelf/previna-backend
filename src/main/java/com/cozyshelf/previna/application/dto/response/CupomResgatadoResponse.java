package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.StatusCupomResgatado;

import java.time.Instant;
import java.time.LocalDate;

// codigo: o que a gestante mostra na loja; saldo: os pontos que sobraram depois do débito
public record CupomResgatadoResponse(
        Long id,
        String codigo,
        String descricao,
        String empresa,
        LocalDate dataValidade,
        StatusCupomResgatado status,
        Instant dataResgate,
        int pontosGastos,
        int saldo
) {}
