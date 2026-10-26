package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.ProdutoRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.ImagemProduto;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.ImagemProdutoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class ProdutoService {
    private final ProdutoRepository produtoRepository;
    private final ImagemProdutoRepository imagemRepository;

    public ProdutoService(ProdutoRepository produtoRepository, ImagemProdutoRepository imagemRepository) {
        this.produtoRepository = produtoRepository;
        this.imagemRepository = imagemRepository;
    }

    @Transactional
    public ProdutoResponse cadastrar(Long idEmpresa, ProdutoRequest dados, MultipartFile imagem) {
        // Valida a imagem antes de salvar o produto
        Optional<Imagem> arquivo = Imagem.de(imagem);

        Produto produto = produtoRepository.save(new Produto(idEmpresa, dados.nome(), dados.descricao(),
                dados.categoria(), dados.preco()));
        arquivo.ifPresent(i -> salvarImagem(produto, i));
        return resposta(produto);
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscar(Long idEmpresa, Long idProduto) {
        return resposta(encontrar(idEmpresa, idProduto));
    }

    // Troca todos os dados; a imagem só muda quando uma nova é enviada
    @Transactional
    public ProdutoResponse atualizar(Long idEmpresa, Long idProduto, ProdutoRequest dados, MultipartFile imagem) {
        Optional<Imagem> arquivo = Imagem.de(imagem);
        Produto produto = encontrar(idEmpresa, idProduto);

        produto.atualizar(dados.nome(), dados.descricao(), dados.categoria(), dados.preco());
        arquivo.ifPresent(i -> salvarImagem(produto, i));
        return resposta(produto);
    }

    // Só os produtos da própria empresa
    @Transactional(readOnly = true)
    public PaginaResponse<ProdutoResponse> listar(Long idEmpresa, ListagemRequest listagem, Categoria categoria) {
        Specification<Produto> filtro = Specification.allOf(
                Filtros.igual("empresaId", idEmpresa),
                Filtros.igual("categoria", categoria),
                Filtros.busca(listagem.busca(), null, "nome"));
        return PaginaResponse.de(produtoRepository.findAll(filtro, listagem.ordenadaPor("nome"))
                .map(ProdutoService::resposta));
    }

    @Transactional(readOnly = true)
    public Optional<ImagemProduto> buscarImagem(Long idProduto) {
        return imagemRepository.findById(idProduto);
    }

    // Produto de outra empresa responde como inexistente
    private Produto encontrar(Long idEmpresa, Long idProduto) {
        return produtoRepository.findByIdAndEmpresaId(idProduto, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Produto não encontrado"));
    }

    private void salvarImagem(Produto produto, Imagem imagem) {
        imagemRepository.save(new ImagemProduto(produto.getId(), imagem.conteudo(), imagem.tipo()));
        // A versão no endereço faz o navegador buscar a imagem nova em vez da que já exibiu
        produto.alterarImagem("/empresas/produtos/%d/imagem?v=%d".formatted(produto.getId(), System.currentTimeMillis()));
    }

    private static ProdutoResponse resposta(Produto produto) {
        return new ProdutoResponse(produto.getId(), produto.getNome(), produto.getDescricao(),
                produto.getCategoria(), produto.getPreco(), produto.getImagemUrl());
    }
}
