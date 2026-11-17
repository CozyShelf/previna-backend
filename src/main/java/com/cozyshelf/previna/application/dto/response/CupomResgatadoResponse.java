package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// Resgate de cupom ou de promoção (tipo). codigo: o que a gestante mostra na loja; saldo: os pontos que sobraram
// depois do débito (na promoção, que não custa pontos, o saldo atual e pontosGastos 0)
public record CupomResgatadoResponse(
        Long id,
        TipoResgate tipo,
        String codigo,
        String descricao,
        String empresa,
        LocalDate dataValidade,
        StatusCupomResgatado status,
        Instant dataResgate,
        int pontosGastos,
        int saldo
) {
    // saldo: o atual da gestante, e não o de quando o resgate foi feito
    public static CupomResgatadoResponse de(CupomResgatado resgatado, int saldo) {
        return new CupomResgatadoResponse(resgatado.getId(), resgatado.getTipo(), resgatado.getCodigo(),
                resgatado.getDescricao(), resgatado.getEmpresaNome(), resgatado.getDataValidade(),
                resgatado.getStatus(), resgatado.getDataResgate().atZone(ZoneId.systemDefault()).toInstant(),
                resgatado.getPontosGastos() == null ? 0 : resgatado.getPontosGastos(), saldo);
    }
}
