package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.response.ListaCurtaResponse;
import com.cozyshelf.previna.application.dto.response.PainelEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.Ofertas;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Painel da tela Início da empresa parceira: tudo filtrado pela empresa do token. Os critérios de cupom válido,
// promoção vigente e produto com oferta são os do catálogo /ofertas (CupomRepository.VALIDO,
// PromocaoRepository.VIGENTE e Ofertas); o de resgate aguardando uso é o da validação na loja
// (CupomResgatadoRepository.AGUARDANDO_USO). Só leitura: não grava log de auditoria
@Service
public class PainelEmpresaService {
    static final int DIAS_A_VENCER = 7;
    static final int DIAS_DE_VALIDACOES = 30;
    private static final Sort PRODUTOS_POR_NOME = Sort.by("nome", "id");

    private final CupomRepository cupomRepository;
    private final PromocaoRepository promocaoRepository;
    private final CupomResgatadoRepository resgateRepository;
    private final ProdutoRepository produtoRepository;

    public PainelEmpresaService(CupomRepository cupomRepository, PromocaoRepository promocaoRepository,
                                CupomResgatadoRepository resgateRepository, ProdutoRepository produtoRepository) {
        this.cupomRepository = cupomRepository;
        this.promocaoRepository = promocaoRepository;
        this.resgateRepository = resgateRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    public PainelEmpresaResponse painel(Long idEmpresa) {
        LocalDate hoje = LocalDate.now();
        return new PainelEmpresaResponse(
                cupomRepository.contarValidos(idEmpresa, hoje),
                promocaoRepository.contarVigentes(idEmpresa, hoje),
                // Cupons e promoções resgatados ainda ATIVO e na validade copiada no resgate
                resgateRepository.contarAguardandoUso(idEmpresa, hoje),
                // Últimos 30 dias contados do instante atual
                resgateRepository.contarUtilizadosDesde(idEmpresa, LocalDateTime.now().minusDays(DIAS_DE_VALIDACOES)),
                ListaCurtaResponse.de(cupomRepository.validosVencendoAte(idEmpresa, hoje, hoje.plusDays(DIAS_A_VENCER),
                                PageRequest.of(0, ListaCurtaResponse.ITENS))
                        .map(c -> new PainelEmpresaResponse.CupomVencendo(c.getId(), c.getProduto().getNome(),
                                c.getDataValidade()))),
                ListaCurtaResponse.de(produtoRepository.findAll(Ofertas.semOferta(idEmpresa, hoje),
                                PageRequest.of(0, ListaCurtaResponse.ITENS, PRODUTOS_POR_NOME))
                        .map(p -> new PainelEmpresaResponse.ProdutoSemOferta(p.getId(), p.getNome()))),
                resgateRepository.findByEmpresaIdAndStatusOrderByDataUsoDescIdDesc(idEmpresa,
                                StatusCupomResgatado.UTILIZADO, Limit.of(ListaCurtaResponse.ITENS)).stream()
                        .map(PainelEmpresaService::validacao).toList());
    }

    private static PainelEmpresaResponse.Validacao validacao(CupomResgatado resgate) {
        boolean promocao = resgate.getTipo() == TipoResgate.PROMOCAO;
        return new PainelEmpresaResponse.Validacao(resgate.getId(), resgate.getCodigo(), resgate.getTipo(),
                promocao ? resgate.getTitulo() : null, promocao ? null : resgate.getProdutoNome(),
                ResgateResponse.instante(resgate.getDataUso()));
    }
}
