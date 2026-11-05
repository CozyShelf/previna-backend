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
        Produto produto = produtoDaEmpresa(idEmpresa, dados.produto());
        Cupom cupom = cupomRepository.save(new Cupom(idEmpresa, produto, dados.desconto(), dados.dataValidade(),
                dados.pontosNecessarios()));
        return resposta(cupom);
    }

    @Transactional(readOnly = true)
    public CupomResponse buscar(Long idEmpresa, Long idCupom) {
        return resposta(encontrar(idEmpresa, idCupom));
    }

    // Troca todos os dados, inclusive o produto; a situação (ativo) continua a mesma
    @Transactional
    public CupomResponse atualizar(Long idEmpresa, Long idCupom, CupomRequest dados) {
        Cupom cupom = encontrar(idEmpresa, idCupom);
        cupom.atualizar(produtoDaEmpresa(idEmpresa, dados.produto()), dados.desconto(), dados.dataValidade(),
                dados.pontosNecessarios());
        return resposta(cupom);
    }

    // Só os cupons da própria empresa, os que vencem antes primeiro; a descrição é montada, então não há busca por texto
    @Transactional(readOnly = true)
    public PaginaResponse<CupomResponse> listar(Long idEmpresa, ListagemRequest listagem) {
        return PaginaResponse.de(cupomRepository.findAll(Filtros.igual("empresaId", idEmpresa),
                listagem.ordenadaPor("dataValidade")).map(CupomService::resposta));
    }

    @Transactional
    public CupomResponse alterarAtivo(Long idEmpresa, Long idCupom, boolean ativo) {
        Cupom cupom = encontrar(idEmpresa, idCupom);
        cupom.alterarAtivo(ativo);
        return resposta(cupom);
    }

    // Cupom de outra empresa responde como inexistente
    private Cupom encontrar(Long idEmpresa, Long idCupom) {
        return cupomRepository.findByIdAndEmpresaId(idCupom, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Cupom não encontrado"));
    }

    // Id de produto de outra empresa (ou inexistente) não volta na busca
    private Produto produtoDaEmpresa(Long idEmpresa, Long idProduto) {
        return produtoRepository.findByIdAndEmpresaId(idProduto, idEmpresa)
                .orElseThrow(() -> new DadosInvalidosException("Produto não pertence à empresa"));
    }

    private static CupomResponse resposta(Cupom cupom) {
        return new CupomResponse(cupom.getId(), cupom.getDescricao(), cupom.getProduto().getNome(),
                cupom.getProduto().getId(), cupom.getDesconto(), cupom.getDataValidade(), cupom.getPontosNecessarios(), cupom.isAtivo());
    }
}
