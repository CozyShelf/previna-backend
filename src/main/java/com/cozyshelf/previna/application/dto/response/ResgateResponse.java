package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.ResgateDaGestante;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

// Cupom ou promoção resgatados, em "meus cupons", montados da cópia gravada no resgate. titulo: no cupom, a frase do
// desconto ("10% de desconto em Fralda RN"); na promoção, o título dela. produto: nulo na promoção. categoria: no cupom,
// a atual do produto (nula se o produto não tem); na promoção, a copiada. pontosGastos: 0 na promoção.
// Nulos vão no JSON (ao contrário do resto da API), como nas ofertas: o app sabe que não há produto nem data de uso.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ResgateResponse(
        Long id,
        TipoResgate tipo,
        String codigo,
        String titulo,
        Empresa empresa,
        String produto,
        Categoria categoria,
        BigDecimal desconto,
        LocalDate dataValidade,
        StatusCupomResgatado status,
        Instant dataResgate,
        Instant dataUso,
        int pontosGastos
) {
    public record Empresa(Long id, String nome) {}

    public static ResgateResponse de(ResgateDaGestante lido) {
        CupomResgatado resgate = lido.resgate();
        boolean promocao = resgate.getTipo() == TipoResgate.PROMOCAO;
        return new ResgateResponse(resgate.getId(), resgate.getTipo(), resgate.getCodigo(),
                promocao ? resgate.getTitulo() : resgate.getDescricao(),
                new Empresa(resgate.getEmpresaId(), resgate.getEmpresaNome()),
                promocao ? null : resgate.getProdutoNome(),
                promocao ? resgate.getCategoria() : lido.categoriaDoProduto(),
                resgate.getDesconto(), resgate.getDataValidade(), resgate.getStatus(),
                instante(resgate.getDataResgate()), instante(resgate.getDataUso()),
                resgate.getPontosGastos() == null ? 0 : resgate.getPontosGastos());
    }

    // Como o dataResgate do CupomResgatadoResponse: a data e hora gravadas estão no fuso da JVM
    private static Instant instante(LocalDateTime dataHora) {
        return dataHora == null ? null : dataHora.atZone(ZoneId.systemDefault()).toInstant();
    }
}
