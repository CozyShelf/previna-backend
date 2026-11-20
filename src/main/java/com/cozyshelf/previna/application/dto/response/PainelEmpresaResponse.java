package com.cozyshelf.previna.application.dto.response;

import com.cozyshelf.previna.domain.model.TipoResgate;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Painel da tela Início da empresa parceira, só com os dados da empresa do token. O significado de cada número está
// no PainelEmpresaService e no docs/README.md
public record PainelEmpresaResponse(
        long cuponsAtivos,
        long promocoesVigentes,
        long resgatesAguardandoUso,
        long validacoes30Dias,
        ListaCurtaResponse<CupomVencendo> cuponsVencendo7Dias,
        ListaCurtaResponse<ProdutoSemOferta> produtosSemOferta,
        List<Validacao> ultimasValidacoes
) {
    public record CupomVencendo(Long cupomId, String produtoNome, LocalDate dataValidade) {}

    public record ProdutoSemOferta(Long produtoId, String nome) {}

    // Da cópia gravada no resgate, como na validação do código: titulo só na promoção, produtoNome só no cupom; o outro
    // vai nulo no JSON. Nada da gestante
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Validacao(Long resgateId, String codigo, TipoResgate tipo, String titulo, String produtoNome,
                            Instant dataUso) {}
}
