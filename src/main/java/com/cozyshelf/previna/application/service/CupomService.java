package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.response.CupomDisponivelResponse;
import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.dto.response.CupomResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CupomService {
    // Sem 0/O, 1/I/L: o código é lido em voz alta ou digitado no caixa da loja
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int TAMANHO_CODIGO = 8;
    // Os mais baratos primeiro; o id desempata
    private static final Sort MAIS_BARATOS = Sort.by("pontosNecessarios", "id");

    private final CupomRepository cupomRepository;
    private final ProdutoRepository produtoRepository;
    private final CupomResgatadoRepository cupomResgatadoRepository;
    private final EmpresaParceiraRepository empresaRepository;
    private final PontuacaoService pontuacaoService;
    private final SecureRandom aleatorio = new SecureRandom();

    public CupomService(CupomRepository cupomRepository, ProdutoRepository produtoRepository,
                        CupomResgatadoRepository cupomResgatadoRepository,
                        EmpresaParceiraRepository empresaRepository, PontuacaoService pontuacaoService) {
        this.cupomRepository = cupomRepository;
        this.produtoRepository = produtoRepository;
        this.cupomResgatadoRepository = cupomResgatadoRepository;
        this.empresaRepository = empresaRepository;
        this.pontuacaoService = pontuacaoService;
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

    // Cupons que a gestante pode resgatar, de todas as empresas: ativos e dentro da validade, os mais baratos primeiro.
    // O app agrupa por empresa.
    @Transactional(readOnly = true)
    public List<CupomDisponivelResponse> listarDisponiveis() {
        List<Cupom> cupons = cupomRepository.findByAtivoTrueAndDataValidadeGreaterThanEqual(LocalDate.now(),
                MAIS_BARATOS);
        Map<Long, EmpresaParceira> empresas = empresaRepository
                .findAllById(cupons.stream().map(Cupom::getEmpresaId).distinct().toList()).stream()
                .collect(Collectors.toMap(EmpresaParceira::getId, Function.identity()));
        return cupons.stream()
                .map(cupom -> disponivel(cupom, empresas.get(cupom.getEmpresaId())))
                .toList();
    }

    // Cupom, débito e saldo na mesma transação, com a gestante travada: dois resgates simultâneos não passam do saldo
    @Transactional
    public CupomResgatadoResponse resgatar(Long idCupom, Long idGestante) {
        Cupom cupom = cupomRepository.findById(idCupom)
                .orElseThrow(() -> new NaoEncontradoException("Cupom não encontrado"));
        Gestante gestante = pontuacaoService.travarGestante(idGestante);

        if (!cupom.disponivelEm(LocalDate.now())) {
            throw new ConflitoException("Cupom indisponível");
        }
        if (gestante.getSaldoPontos() < cupom.getPontosNecessarios()) {
            throw new ConflitoException("Pontos insuficientes");
        }

        CupomResgatado resgatado = cupomResgatadoRepository.save(new CupomResgatado(cupom, gestante, gerarCodigoUnico()));
        int saldo = pontuacaoService.debitarPontos(gestante, cupom.getPontosNecessarios(), resgatado);
        String empresa = empresaRepository.findById(cupom.getEmpresaId()).map(EmpresaParceira::getRazaoSocial)
                .orElse(null);
        return new CupomResgatadoResponse(resgatado.getId(), resgatado.getCodigo(), cupom.getDescricao(), empresa,
                cupom.getDataValidade(), resgatado.getStatus(),
                resgatado.getDataResgate().atZone(ZoneId.systemDefault()).toInstant(), cupom.getPontosNecessarios(),
                saldo);
    }

    // Confere antes de usar; o UNIQUE de cupom_resgatado.codigo é a última barreira se dois resgates sortearem o mesmo
    private String gerarCodigoUnico() {
        String codigo;
        do {
            StringBuilder sorteado = new StringBuilder(TAMANHO_CODIGO);
            for (int i = 0; i < TAMANHO_CODIGO; i++) {
                sorteado.append(ALFABETO_CODIGO.charAt(aleatorio.nextInt(ALFABETO_CODIGO.length())));
            }
            codigo = sorteado.toString();
        } while (cupomResgatadoRepository.existsByCodigo(codigo));
        return codigo;
    }

    private static CupomDisponivelResponse disponivel(Cupom cupom, EmpresaParceira empresa) {
        Produto produto = cupom.getProduto();
        var dadosEmpresa = empresa == null ? null : new CupomDisponivelResponse.Empresa(empresa.getId(),
                empresa.getRazaoSocial(), empresa.getSegmento(), empresa.getLogoUrl());
        return new CupomDisponivelResponse(cupom.getId(), cupom.getDescricao(), produto.getNome(),
                produto.getCategoria(), produto.getImagemUrl(), cupom.getDesconto(), cupom.getDataValidade(),
                cupom.getPontosNecessarios(), dadosEmpresa);
    }

    private static CupomResponse resposta(Cupom cupom) {
        return new CupomResponse(cupom.getId(), cupom.getDescricao(), cupom.getProduto().getNome(),
                cupom.getDesconto(), cupom.getDataValidade(), cupom.getPontosNecessarios(), cupom.isAtivo());
    }
}
