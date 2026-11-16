package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.OfertaRequest;
import com.cozyshelf.previna.application.dto.response.OfertaEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaDetalheResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaResumoResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.Ofertas;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Catálogo de ofertas da gestante: empresas com os produtos que têm cupom válido ou promoção vigente (regras em
// Ofertas). Os filtros ficam no servidor porque o app pagina com rolagem infinita.
@Service
public class OfertaService {
    // Quantos produtos cada empresa mostra na lista; os demais ficam no "Ver todos"
    static final int PRODUTOS_POR_EMPRESA = 10;
    private static final String PRODUTO_NAO_ENCONTRADO = "Produto não encontrado";

    // Ordens fixas; o id desempata nomes iguais, e nenhum registro aparece em duas páginas
    private static final Sort EMPRESAS_POR_NOME = Sort.by("razaoSocial", "id");
    private static final Sort PRODUTOS_POR_NOME = Sort.by("nome", "id");
    private static final Sort MAIS_BARATOS = Sort.by("pontosNecessarios", "id");
    private static final Sort MAIOR_DESCONTO = Sort.by(Sort.Order.desc("desconto"), Sort.Order.asc("id"));

    private final EmpresaParceiraRepository empresaRepository;
    private final ProdutoRepository produtoRepository;
    private final CupomRepository cupomRepository;
    private final PromocaoRepository promocaoRepository;
    private final GestanteRepository gestanteRepository;

    public OfertaService(EmpresaParceiraRepository empresaRepository, ProdutoRepository produtoRepository,
                         CupomRepository cupomRepository, PromocaoRepository promocaoRepository,
                         GestanteRepository gestanteRepository) {
        this.empresaRepository = empresaRepository;
        this.produtoRepository = produtoRepository;
        this.cupomRepository = cupomRepository;
        this.promocaoRepository = promocaoRepository;
        this.gestanteRepository = gestanteRepository;
    }

    // Página de empresas (por nome), cada uma com os primeiros produtos com oferta (por nome) e o total deles.
    // shortcut: duas consultas de produtos por empresa da página (a lista e a contagem), até 2 x 100; trocar por uma
    // consulta com janela (row_number() over partition by empresa) se o catálogo crescer
    @Transactional(readOnly = true)
    public PaginaResponse<OfertaEmpresaResponse> listarEmpresas(Long idGestante, OfertaRequest filtros) {
        Ofertas.Filtro filtro = filtro(idGestante, filtros);
        Page<EmpresaParceira> empresas = empresaRepository.findAll(Ofertas.empresas(filtro),
                filtros.paginacao(EMPRESAS_POR_NOME));
        Map<Long, Page<Produto>> produtos = empresas.stream().collect(Collectors.toMap(EmpresaParceira::getId,
                empresa -> produtoRepository.findAll(Ofertas.produtos(filtro.daEmpresa(empresa.getId())),
                        PageRequest.of(0, PRODUTOS_POR_EMPRESA, PRODUTOS_POR_NOME))));
        Function<Produto, ProdutoOfertaResumoResponse> resumo = resumidor(filtro.dia(),
                produtos.values().stream().flatMap(Page::stream).toList());
        return PaginaResponse.de(empresas.map(empresa -> {
            Page<Produto> daEmpresa = produtos.get(empresa.getId());
            return new OfertaEmpresaResponse(empresa.getId(), empresa.getRazaoSocial(), empresa.getSegmento(),
                    empresa.getLogoUrl(), daEmpresa.getTotalElements(), daEmpresa.map(resumo).getContent());
        }));
    }

    // "Ver todos" de uma empresa: os produtos com oferta, por nome, com os mesmos filtros (a empresa é a do caminho)
    @Transactional(readOnly = true)
    public PaginaResponse<ProdutoOfertaResumoResponse> listarProdutos(Long idGestante, Long idEmpresa,
                                                                      OfertaRequest filtros) {
        if (!empresaRepository.existsById(idEmpresa)) {
            throw new NaoEncontradoException("Empresa não encontrada");
        }
        Ofertas.Filtro filtro = filtro(idGestante, filtros).daEmpresa(idEmpresa);
        Page<Produto> produtos = produtoRepository.findAll(Ofertas.produtos(filtro),
                filtros.paginacao(PRODUTOS_POR_NOME));
        return PaginaResponse.de(produtos.map(resumidor(filtro.dia(), produtos.getContent())));
    }

    // Produto sem nenhuma oferta responde como inexistente, como no catálogo
    @Transactional(readOnly = true)
    public ProdutoOfertaDetalheResponse detalhar(Long idProduto) {
        LocalDate hoje = LocalDate.now();
        Produto produto = produtoRepository.findById(idProduto)
                .orElseThrow(() -> new NaoEncontradoException(PRODUTO_NAO_ENCONTRADO));
        List<Cupom> cupons = cupomRepository.validos(List.of(produto.getId()), hoje, MAIS_BARATOS);
        List<Promocao> promocoes = promocaoRepository.vigentes(List.of(produto.getEmpresaId()), hoje, MAIOR_DESCONTO)
                .stream().filter(promocao -> promocao.valePara(produto)).toList();
        if (cupons.isEmpty() && promocoes.isEmpty()) {
            throw new NaoEncontradoException(PRODUTO_NAO_ENCONTRADO);
        }
        EmpresaParceira empresa = empresaRepository.findById(produto.getEmpresaId()).orElseThrow();
        return new ProdutoOfertaDetalheResponse(produto.getId(), produto.getNome(), produto.getCategoria(),
                produto.getImagemUrl(),
                new ProdutoOfertaDetalheResponse.Empresa(empresa.getId(), empresa.getRazaoSocial(),
                        empresa.getSegmento(), empresa.getLogoUrl()),
                cupons.stream().map(cupom -> new ProdutoOfertaDetalheResponse.Cupom(cupom.getId(),
                        cupom.getDesconto(), cupom.getPontosNecessarios(), cupom.getDataValidade())).toList(),
                promocoes.stream().map(promocao -> new ProdutoOfertaDetalheResponse.Promocao(promocao.getId(),
                        promocao.getTitulo(), promocao.getDesconto(), promocao.getDataInicio(),
                        promocao.getDataFim())).toList());
    }

    // O saldo só é lido com "cabe no meu saldo"; a gestante vem do token
    private Ofertas.Filtro filtro(Long idGestante, OfertaRequest filtros) {
        Integer pontosAte = filtros.soAteMeusPontos()
                ? gestanteRepository.findById(idGestante).map(Gestante::getSaldoPontos)
                        .orElseThrow(() -> new NaoEncontradoException("Gestante não encontrada"))
                : null;
        return new Ofertas.Filtro(filtros.busca(), filtros.categoria(), filtros.empresaId(), filtros.tipo(),
                pontosAte, LocalDate.now());
    }

    // Os selos de cada produto (menor custo e maior desconto dos cupons, maior desconto das promoções), com uma
    // consulta de cupons e uma de promoções para todos os produtos, em vez de duas por produto.
    // Mostra todas as ofertas do produto, mesmo as que um filtro de tipo deixaria de fora.
    private Function<Produto, ProdutoOfertaResumoResponse> resumidor(LocalDate dia, List<Produto> produtos) {
        Map<Long, List<Cupom>> cupons = cupomRepository
                .validos(produtos.stream().map(Produto::getId).toList(), dia, MAIS_BARATOS).stream()
                .collect(Collectors.groupingBy(cupom -> cupom.getProduto().getId()));
        List<Promocao> promocoes = promocaoRepository
                .vigentes(produtos.stream().map(Produto::getEmpresaId).distinct().toList(), dia, MAIOR_DESCONTO);
        return produto -> {
            List<Cupom> doProduto = cupons.getOrDefault(produto.getId(), List.of());
            return new ProdutoOfertaResumoResponse(produto.getId(), produto.getNome(), produto.getCategoria(),
                    produto.getImagemUrl(),
                    doProduto.stream().map(Cupom::getPontosNecessarios).min(Comparator.naturalOrder()).orElse(null),
                    doProduto.stream().map(Cupom::getDesconto).max(Comparator.naturalOrder()).orElse(null),
                    promocoes.stream().filter(promocao -> promocao.valePara(produto)).map(Promocao::getDesconto)
                            .max(Comparator.naturalOrder()).orElse(null));
        };
    }
}
