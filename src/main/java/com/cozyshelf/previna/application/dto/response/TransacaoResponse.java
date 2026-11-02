package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.TipoTransacao;

import java.time.Instant;

// descricao: a consulta ou exame que gerou o crédito, ou o resgate que gerou o débito
public record TransacaoResponse(
        Long id,
        TipoTransacao tipo,
        int pontos,
        String descricao,
        Instant dataHora
) {}
