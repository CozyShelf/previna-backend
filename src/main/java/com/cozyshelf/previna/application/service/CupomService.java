package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.response.CupomResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CupomService {
    private final CupomRepository cupomRepository;
    private final ProdutoRepository produtoRepository;

    public CupomService(CupomRepository cupomRepository, ProdutoRepository produtoRepository) {
        this.cupomRepository = cupomRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional
    public CupomResponse cadastrar(Long idEmpresa, CupomRequest dados) {
        // Id de produto de outra empresa (ou inexistente) não volta na busca
        Produto produto = produtoRepository.findByIdAndEmpresaId(dados.produto(), idEmpresa)
                .orElseThrow(() -> new DadosInvalidosException("Produto não pertence à empresa"));

        Cupom cupom = cupomRepository.save(new Cupom(idEmpresa, produto, dados.desconto(), dados.dataValidade(),
                dados.pontosNecessarios()));
        return resposta(cupom);
    }

    // Só os cupons da própria empresa, os que vencem antes primeiro; a descrição é montada, então não há busca por texto
    @Transactional(readOnly = true)
    public PaginaResponse<CupomResponse> listar(Long idEmpresa, ListagemRequest listagem) {
        return PaginaResponse.de(cupomRepository.findAll(Filtros.igual("empresaId", idEmpresa),
                listagem.ordenadaPor("dataValidade")).map(CupomService::resposta));
    }

    // Cupom de outra empresa responde como inexistente
    @Transactional
    public CupomResponse alterarAtivo(Long idEmpresa, Long idCupom, boolean ativo) {
        Cupom cupom = cupomRepository.findByIdAndEmpresaId(idCupom, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Cupom não encontrado"));
        cupom.alterarAtivo(ativo);
        return resposta(cupom);
    }

    private static CupomResponse resposta(Cupom cupom) {
        return new CupomResponse(cupom.getId(), cupom.getDescricao(), cupom.getProduto().getNome(),
                cupom.getDesconto(), cupom.getDataValidade(), cupom.getPontosNecessarios(), cupom.isAtivo());
    }
}
