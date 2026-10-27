package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.application.dto.response.TipoAtendimentoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AgendamentoService {
    private final TipoAtendimentoRepository tipoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final MedicoRepository medicoRepository;
    private final GestanteService gestanteService;
    private final PontuacaoService pontuacaoService;

    public AgendamentoService(TipoAtendimentoRepository tipoRepository, AgendamentoRepository agendamentoRepository,
                              MedicoRepository medicoRepository, GestanteService gestanteService,
                              PontuacaoService pontuacaoService) {
        this.tipoRepository = tipoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.medicoRepository = medicoRepository;
        this.gestanteService = gestanteService;
        this.pontuacaoService = pontuacaoService;
    }

    // Só os tipos ativos: um tipo desativado não pode mais ser escolhido, mas continua nos agendamentos antigos
    @Transactional(readOnly = true)
    public List<TipoAtendimentoResponse> listarTiposAtivos(CategoriaAtendimento categoria) {
        List<TipoAtendimento> tipos = categoria == null
                ? tipoRepository.findByAtivoTrueOrderByNomeAsc()
                : tipoRepository.findByCategoriaAndAtivoTrueOrderByNomeAsc(categoria);
        return tipos.stream().map(AgendamentoService::tipoResposta).toList();
    }

    // Nenhum ponto é creditado aqui: os pontos do tipo só saem quando o médico confirmar a realização
    @Transactional
    public AgendamentoResponse agendar(AgendamentoRequest dados, Long idMedico) {
        // A gestante é conferida antes do tipo: 404 antes de 400, como no diagrama
        Gestante gestante = gestanteService.gestanteDoMedico(idMedico, dados.gestante());
        TipoAtendimento tipo = tipoRepository.findById(dados.tipoAtendimento())
                .filter(TipoAtendimento::isAtivo)
                .orElseThrow(() -> new DadosInvalidosException("Tipo de atendimento inválido"));

        return resposta(agendamentoRepository.save(new Agendamento(gestante, tipo,
                medicoRepository.getReferenceById(idMedico), dados.dataPrevista(), dados.observacao())));
    }

    // Pendentes primeiro (a ordem do enum é a de exibição), depois pela data prevista; o id desempata.
    // A ordenação é feita aqui, e não no banco, porque ordenar pelo texto do status daria a ordem alfabética.
    private static final Comparator<Agendamento> ORDEM = Comparator
            .comparingInt((Agendamento a) -> a.getStatus().ordinal())
            .thenComparing(Agendamento::getDataPrevista)
            .thenComparing(Agendamento::getId);

    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarDaGestante(Long idMedico, Long idGestante) {
        // Confere o alcance do médico antes de listar; o id vem do caminho, já validado por essa busca
        gestanteService.gestanteDoMedico(idMedico, idGestante);
        return ordenados(idGestante);
    }

    private List<AgendamentoResponse> ordenados(Long idGestante) {
        return agendamentoRepository.findByGestanteId(idGestante).stream()
                .sorted(ORDEM)
                .map(AgendamentoService::resposta)
                .toList();
    }

    // No app, o id vem do token: a gestante só alcança os próprios, sem regra de médico
    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarDaGestanteAutenticada(Long idGestante) {
        return ordenados(idGestante);
    }

    // Status, transação de pontos, saldo e resposta na mesma transação: ou tudo é gravado, ou nada
    @Transactional
    public ConfirmacaoResponse confirmar(Long idAgendamento, Long idMedico) {
        Agendamento agendamento = resolver(idAgendamento, idMedico, StatusAgendamento.REALIZADO);
        int pontos = pontuacaoService.creditarPorAgendamento(agendamento);
        return new ConfirmacaoResponse(resposta(agendamento), pontos, agendamento.getGestante().getSaldoPontos());
    }

    // Faltar não credita nada; o registro serve para o acompanhamento do pré-natal
    @Transactional
    public AgendamentoResponse registrarNaoComparecimento(Long idAgendamento, Long idMedico) {
        return resposta(resolver(idAgendamento, idMedico, StatusAgendamento.NAO_COMPARECEU));
    }

    // Agendamento fora do alcance do médico responde como inexistente, com a mensagem do agendamento:
    // a mensagem da gestante revelaria qual dos dois existe
    private Agendamento resolver(Long idAgendamento, Long idMedico, StatusAgendamento desfecho) {
        Agendamento agendamento = agendamentoRepository.findById(idAgendamento)
                .filter(a -> gestanteService.podeAcessar(idMedico, a.getGestante()))
                .orElseThrow(() -> new NaoEncontradoException("Agendamento não encontrado"));
        if (!agendamento.pendente()) {
            throw new ConflitoException("Este agendamento já foi resolvido");
        }
        agendamento.registrarDesfecho(desfecho, medicoRepository.getReferenceById(idMedico));
        return agendamento;
    }

    private static TipoAtendimentoResponse tipoResposta(TipoAtendimento tipo) {
        return new TipoAtendimentoResponse(tipo.getId(), tipo.getNome(), tipo.getCategoria(), tipo.getPontosBase());
    }

    static AgendamentoResponse resposta(Agendamento agendamento) {
        TipoAtendimento tipo = agendamento.getTipo();
        return new AgendamentoResponse(agendamento.getId(), tipo.getNome(), tipo.getCategoria(),
                tipo.getPontosBase(), agendamento.getDataPrevista(), agendamento.getStatus(),
                agendamento.getDataConfirmacao(), agendamento.getObservacao());
    }
}
