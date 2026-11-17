package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PromocaoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class PromocaoService {
    // Índice único da V22: cada gestante resgata uma promoção uma vez só
    static final String RESGATE_UMA_VEZ = "cupom_resgatado_promocao_uma_vez";
    static final String MENSAGEM_JA_RESGATADA = "Promoção já resgatada";

    private final PromocaoRepository promocaoRepository;
    private final CupomResgatadoRepository cupomResgatadoRepository;
    private final EmpresaParceiraRepository empresaRepository;
    private final PontuacaoService pontuacaoService;
    private final GeradorCodigoResgate geradorCodigo;

    public PromocaoService(PromocaoRepository promocaoRepository, CupomResgatadoRepository cupomResgatadoRepository,
                           EmpresaParceiraRepository empresaRepository, PontuacaoService pontuacaoService,
                           GeradorCodigoResgate geradorCodigo) {
        this.promocaoRepository = promocaoRepository;
        this.cupomResgatadoRepository = cupomResgatadoRepository;
        this.empresaRepository = empresaRepository;
        this.pontuacaoService = pontuacaoService;
        this.geradorCodigo = geradorCodigo;
    }

    @Transactional
    public PromocaoResponse cadastrar(Long idEmpresa, PromocaoRequest dados) {
        validarPeriodo(dados);
        Promocao promocao = promocaoRepository.save(new Promocao(idEmpresa, dados.titulo(), dados.descricao(),
                dados.desconto(), dados.dataInicio(), dados.dataFim(), dados.categoria()));
        return resposta(promocao);
    }

    // Só as promoções da própria empresa
    @Transactional(readOnly = true)
    public PaginaResponse<PromocaoResponse> listar(Long idEmpresa, ListagemRequest listagem) {
        Specification<Promocao> filtro = Specification.allOf(
                Filtros.igual("empresaId", idEmpresa),
                Filtros.busca(listagem.busca(), null, "titulo"));
        return PaginaResponse.de(promocaoRepository.findAll(filtro, listagem.maisRecentes())
                .map(PromocaoService::resposta));
    }

    @Transactional(readOnly = true)
    public PromocaoResponse buscar(Long idEmpresa, Long idPromocao) {
        return resposta(encontrar(idEmpresa, idPromocao));
    }

    // Troca todos os dados; a situação (ativo) continua a mesma
    @Transactional
    public PromocaoResponse atualizar(Long idEmpresa, Long idPromocao, PromocaoRequest dados) {
        validarPeriodo(dados);
        Promocao promocao = encontrarParaAtualizar(idEmpresa, idPromocao);
        promocao.atualizar(dados.titulo(), dados.descricao(), dados.desconto(), dados.dataInicio(), dados.dataFim(),
                dados.categoria());
        return resposta(promocao);
    }

    @Transactional
    public PromocaoResponse alterarAtivo(Long idEmpresa, Long idPromocao, boolean ativo) {
        Promocao promocao = encontrarParaAtualizar(idEmpresa, idPromocao);
        promocao.alterarAtivo(ativo);
        return resposta(promocao);
    }

    // Promoção de outra empresa responde como inexistente
    private Promocao encontrar(Long idEmpresa, Long idPromocao) {
        return promocaoRepository.findByIdAndEmpresaId(idPromocao, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Promoção não encontrada"));
    }

    // Travada até o fim da transação: quem grava a promoção espera quem já a está alterando
    private Promocao encontrarParaAtualizar(Long idEmpresa, Long idPromocao) {
        return promocaoRepository.findByIdAndEmpresaIdParaAtualizar(idPromocao, idEmpresa)
                .orElseThrow(() -> new NaoEncontradoException("Promoção não encontrada"));
    }

    private static void validarPeriodo(PromocaoRequest dados) {
        if (dados.dataFim().isBefore(dados.dataInicio())) {
            throw new DadosInvalidosException("A data de fim deve ser igual ou posterior à de início");
        }
    }

    private static PromocaoResponse resposta(Promocao promocao) {
        return new PromocaoResponse(promocao.getId(), promocao.getTitulo(), promocao.getDescricao(),
                promocao.getDesconto(), promocao.getDataInicio(), promocao.getDataFim(), promocao.getCategoria(), promocao.isAtivo());
    }

    // ---- Resgate pela gestante ----

    // Promoção resgatável: não custa pontos e cada gestante a resgata uma vez só, com um código próprio que vale até
    // o fim da promoção. Mesmo fluxo do resgate de cupom (CupomService.resgatar), sem débito.
    // A promoção é lida sem trava: o "uma vez" é do índice único, e ativo e período são conferidos agora
    @Transactional
    public CupomService.Resgate resgatar(Long idPromocao, Long idGestante, UUID chaveIdempotencia) {
        Promocao promocao = promocaoRepository.findById(idPromocao)
                .orElseThrow(() -> new NaoEncontradoException("Promoção não encontrada"));
        Gestante gestante = pontuacaoService.travarGestante(idGestante);

        // Depois da trava, como no cupom: o toque repetido se enfileira nela e encontra o resgate do primeiro.
        // Antes das regras: a promoção pode ter terminado, e o toque repetido não é um resgate novo
        if (chaveIdempotencia != null) {
            Optional<CupomResgatado> feito = cupomResgatadoRepository
                    .findByGestanteIdAndChaveIdempotencia(idGestante, chaveIdempotencia);
            if (feito.isPresent()) {
                return new CupomService.Resgate(CupomResgatadoResponse.de(feito.get(), gestante.getSaldoPontos()),
                        true);
            }
        }

        if (!promocao.vigenteEm(LocalDate.now())) {
            throw new ConflitoException("Promoção indisponível");
        }

        EmpresaParceira empresa = empresaRepository.findById(promocao.getEmpresaId()).orElseThrow();
        CupomResgatado resgatado = new CupomResgatado(promocao, empresa, gestante, geradorCodigo.gerarCodigoUnico(),
                chaveIdempotencia);
        // Quem decide se já foi resgatada é o índice único, e não uma consulta antes. O flush aqui dentro faz a
        // violação sair deste método, e não do commit
        try {
            cupomResgatadoRepository.saveAndFlush(resgatado);
        } catch (DataIntegrityViolationException e) {
            if (RestricoesDoBanco.violou(e, RESGATE_UMA_VEZ)) {
                throw new ConflitoException(MENSAGEM_JA_RESGATADA);
            }
            throw e;
        }
        // Sem débito: o saldo continua o mesmo
        return new CupomService.Resgate(CupomResgatadoResponse.de(resgatado, gestante.getSaldoPontos()), false);
    }
}
