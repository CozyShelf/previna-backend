package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.ResgateDaEmpresa;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Código validado na loja: o que a empresa precisa para aplicar o desconto, montado da cópia gravada no resgate (D2).
// titulo: só na promoção; produtoNome: só no cupom (o outro vai nulo). categoria e desconto como no ResgateResponse.
// Da gestante, só o nome: CPF, e-mail e telefone não saem para a empresa. Os nulos vão no JSON, como no ResgateResponse
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ValidacaoCupomResponse(
        Long id,
        TipoResgate tipo,
        String codigo,
        String titulo,
        String produtoNome,
        Categoria categoria,
        BigDecimal desconto,
        LocalDate validade,
        Instant dataResgate,
        Instant dataUso,
        String gestanteNome
) {
    public static ValidacaoCupomResponse de(ResgateDaEmpresa lido) {
        CupomResgatado resgate = lido.resgate();
        boolean promocao = resgate.getTipo() == TipoResgate.PROMOCAO;
        return new ValidacaoCupomResponse(resgate.getId(), resgate.getTipo(), resgate.getCodigo(),
                promocao ? resgate.getTitulo() : null,
                promocao ? null : resgate.getProdutoNome(),
                promocao ? resgate.getCategoria() : lido.categoriaDoProduto(),
                resgate.getDesconto(), resgate.getDataValidade(),
                ResgateResponse.instante(resgate.getDataResgate()), ResgateResponse.instante(resgate.getDataUso()),
                lido.gestanteNome());
    }
}
