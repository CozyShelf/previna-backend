package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.AtendimentoDoDiaResponse;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class AgendamentoService {
    private final TipoAtendimentoService tipoService;
    private final AgendamentoRepository agendamentoRepository;
    private final MedicoRepository medicoRepository;
    private final GestanteService gestanteService;
    private final PontuacaoService pontuacaoService;

    public AgendamentoService(TipoAtendimentoService tipoService, AgendamentoRepository agendamentoRepository,
                              MedicoRepository medicoRepository, GestanteService gestanteService,
                              PontuacaoService pontuacaoService) {
        this.tipoService = tipoService;
        this.agendamentoRepository = agendamentoRepository;
        this.medicoRepository = medicoRepository;
        this.gestanteService = gestanteService;
        this.pontuacaoService = pontuacaoService;
    }

    // Cada item vira um agendamento independente, com status e confirmação próprios. Tudo na mesma
    // transação: um tipo inválido no meio da lista não deixa metade do cadastro gravada.
    // Nenhum ponto é creditado aqui: os pontos do tipo só saem quando o médico confirmar a realização.
    @Transactional
    public List<AgendamentoResponse> agendar(AgendamentoRequest dados, Long idMedico) {
        // A gestante é conferida antes dos tipos: 404 antes de 400, como no diagrama
        Gestante gestante = gestanteService.gestanteDoMedico(idMedico, dados.gestante());
        Medico medico = medicoRepository.getReferenceById(idMedico);

        List<Agendamento> novos = dados.itens().stream()
                .map(item -> new Agendamento(gestante, tipoService.paraAgendamento(idMedico, item.tipoAtendimento()),
                        medico, item.dataPrevista(), item.observacao()))
                .toList();
        return agendamentoRepository.saveAll(novos).stream().map(AgendamentoService::resposta).toList();
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

    // Tela inicial do médico: o que está pendente para hoje, no mesmo alcance das demais telas dele
    // (as gestantes que acompanha, ou todas quando o administrador deu essa permissão).
    @Transactional(readOnly = true)
    public List<AtendimentoDoDiaResponse> listarDeHoje(Long idMedico) {
        LocalDate hoje = LocalDate.now();
        List<Agendamento> doDia = medicoRepository.findById(idMedico).orElseThrow().podeVerTodasGestantes()
                ? agendamentoRepository.findByStatusAndDataPrevistaOrderByIdAsc(StatusAgendamento.AGENDADO, hoje)
                : agendamentoRepository.findByStatusAndDataPrevistaAndGestanteMedicoIdOrderByIdAsc(
                        StatusAgendamento.AGENDADO, hoje, idMedico);

        return doDia.stream()
                .map(a -> new AtendimentoDoDiaResponse(a.getId(), a.getGestante().getId(),
                        a.getGestante().getNome(), a.getTipo().getNome(), a.getTipo().getCategoria()))
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
        Agendamento agendamento = agendamentoRepository.findParaResolver(idAgendamento)
                .filter(a -> gestanteService.podeAcessar(idMedico, a.getGestante()))
                .orElseThrow(() -> new NaoEncontradoException("Agendamento não encontrado"));
        if (!agendamento.pendente()) {
            throw new ConflitoException("Este agendamento já foi resolvido");
        }
        // Só a data, sem horário: no próprio dia o desfecho já vale. O fuso é o padrão da JVM,
        // fixado em America/Sao_Paulo na PrevinaApplication.
        if (agendamento.getDataPrevista().isAfter(LocalDate.now())) {
            throw new ConflitoException("Este agendamento só pode ser resolvido a partir da data prevista");
        }
        agendamento.registrarDesfecho(desfecho, medicoRepository.getReferenceById(idMedico));
        return agendamento;
    }

    static AgendamentoResponse resposta(Agendamento agendamento) {
        TipoAtendimento tipo = agendamento.getTipo();
        return new AgendamentoResponse(agendamento.getId(), tipo.getNome(), tipo.getCategoria(),
                tipo.getPontosBase(), agendamento.getDataPrevista(), agendamento.getStatus(),
                agendamento.getDataConfirmacao(), agendamento.getObservacao());
    }
}
