package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.TipoAtendimentoResponse;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Gestante;
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

    public AgendamentoService(TipoAtendimentoRepository tipoRepository, AgendamentoRepository agendamentoRepository,
                              MedicoRepository medicoRepository, GestanteService gestanteService) {
        this.tipoRepository = tipoRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.medicoRepository = medicoRepository;
        this.gestanteService = gestanteService;
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
        return agendamentoRepository.findByGestanteId(idGestante).stream()
                .sorted(ORDEM)
                .map(AgendamentoService::resposta)
                .toList();
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
