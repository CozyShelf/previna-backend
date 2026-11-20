package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.dto.response.CupomResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.exception.CodigoErro;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class CupomService {
    private final CupomRepository cupomRepository;
    private final ProdutoRepository produtoRepository;
    private final CupomResgatadoRepository cupomResgatadoRepository;
    private final EmpresaParceiraRepository empresaRepository;
    private final PontuacaoService pontuacaoService;
    private final GeradorCodigoResgate geradorCodigo;

    public CupomService(CupomRepository cupomRepository, ProdutoRepository produtoRepository,
                        CupomResgatadoRepository cupomResgatadoRepository,
                        EmpresaParceiraRepository empresaRepository, PontuacaoService pontuacaoService,
                        GeradorCodigoResgate geradorCodigo) {
        this.cupomRepository = cupomRepository;
        this.produtoRepository = produtoRepository;
        this.cupomResgatadoRepository = cupomResgatadoRepository;
        this.empresaRepository = empresaRepository;
        this.pontuacaoService = pontuacaoService;
        this.geradorCodigo = geradorCodigo;
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
        Cupom cupom = encontrarParaAtualizar(idEmpresa, idCupom);
        cupom.atualizar(produtoDaEmpresa(idEmpresa, dados.produto()), dados.desconto(), dados.dataValidade(),
                dados.pontosNecessarios());
        return resposta(cupom);
    }

    // Só os cupons da própria empresa, os que vencem antes primeiro; a descrição é montada, então não há busca por texto
    @Transactional(readOnly = true)
    public PaginaResponse<CupomResponse> listar(Long idEmpresa, ListagemRequest listagem) {
        return PaginaResponse.de(cupomRepository.findAll(Filtros.igual("empresaId", idEmpresa),
                listagem.maisRecentes()).map(CupomService::resposta));
    }

    @Transactional
    public CupomResponse alterarAtivo(Long idEmpresa, Long idCupom, boolean ativo) {
        Cupom cupom = encontrarParaAtualizar(idEmpresa, idCupom);
        cupom.alterarAtivo(ativo);
        return resposta(cupom);
    }

    // Cupom de outra empresa responde como inexistente
    private Cupom encontrar(Long idEmpresa, Long idCupom) {
        return cupomRepository.findByIdAndEmpresaId(idCupom, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Cupom não encontrado"));
    }

    // Travado até o fim da transação: quem grava o cupom espera quem já o está alterando
    private Cupom encontrarParaAtualizar(Long idEmpresa, Long idCupom) {
        return cupomRepository.findByIdAndEmpresaIdParaAtualizar(idCupom, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Cupom não encontrado"));
    }

    // Id de produto de outra empresa (ou inexistente) não volta na busca
    private Produto produtoDaEmpresa(Long idEmpresa, Long idProduto) {
        return produtoRepository.findByIdAndEmpresaId(idProduto, idEmpresa)
                .orElseThrow(() -> new DadosInvalidosException("Produto não pertence à empresa"));
    }

    // Resultado do resgate, de cupom ou de promoção. repetido: a chave de idempotência já tinha sido usada, e o cupom é
    // o do resgate feito antes
    public record Resgate(CupomResgatadoResponse cupom, boolean repetido) {}

    // Cupom, débito e saldo na mesma transação, com a gestante travada: dois resgates simultâneos não passam do saldo.
    // chaveIdempotencia (opcional) identifica a confirmação no app: o duplo toque devolve o resgate já feito, sem
    // debitar de novo. Sem chave (versões antigas do app), cada chamada é um resgate novo.
    @Transactional
    public Resgate resgatar(Long idCupom, Long idGestante, UUID chaveIdempotencia) {
        Cupom cupom = cupomRepository.findById(idCupom)
                .orElseThrow(() -> new NaoEncontradoException("Cupom não encontrado"));
        Gestante gestante = pontuacaoService.travarGestante(idGestante);

        // Depois da trava: os dois toques se enfileiram nela, e o segundo já encontra o resgate do primeiro.
        // Antes das regras: o cupom pode ter esgotado o saldo, e o toque repetido não é um resgate novo.
        // O UNIQUE (gestante_id, chave_idempotencia) é a última barreira
        if (chaveIdempotencia != null) {
            Optional<CupomResgatado> feito = cupomResgatadoRepository
                    .findByGestanteIdAndChaveIdempotencia(idGestante, chaveIdempotencia);
            if (feito.isPresent()) {
                return new Resgate(CupomResgatadoResponse.de(feito.get(), gestante.getSaldoPontos()), true);
            }
        }

        if (!cupom.disponivelEm(LocalDate.now())) {
            throw new ConflitoException("Cupom indisponível", CodigoErro.CUPOM_INDISPONIVEL);
        }
        if (gestante.getSaldoPontos() < cupom.getPontosNecessarios()) {
            throw new ConflitoException("Pontos insuficientes", CodigoErro.PONTOS_INSUFICIENTES);
        }

        // O cupom resgatado guarda uma cópia do cupom e da empresa: editar o cupom depois não muda este resgate
        EmpresaParceira empresa = empresaRepository.findById(cupom.getEmpresaId()).orElseThrow();
        CupomResgatado resgatado = cupomResgatadoRepository.save(
                new CupomResgatado(cupom, empresa, gestante, geradorCodigo.gerarCodigoUnico(), chaveIdempotencia));
        int saldo = pontuacaoService.debitarPontos(gestante, resgatado.getPontosGastos(), resgatado);
        return new Resgate(CupomResgatadoResponse.de(resgatado, saldo), false);
    }

    private static CupomResponse resposta(Cupom cupom) {
        return new CupomResponse(cupom.getId(), cupom.getDescricao(), cupom.getProduto().getNome(),
                cupom.getProduto().getId(), cupom.getDesconto(), cupom.getDataValidade(), cupom.getPontosNecessarios(), cupom.isAtivo());
    }
}
