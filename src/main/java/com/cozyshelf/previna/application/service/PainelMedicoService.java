package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.response.ListaCurtaResponse;
import com.cozyshelf.previna.application.dto.response.PainelMedicoResponse;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// Painel da tela Início do médico. Cada número é um count no banco e cada lista, uma página com os primeiros e o total.
// Só leitura: não grava log de auditoria
@Service
public class PainelMedicoService {
    // A ordem de cada lista vem da consulta
    private static final Pageable PRIMEIROS = PageRequest.of(0, ListaCurtaResponse.ITENS);
    // Amanhã até hoje + 7: hoje fica de fora porque já está na agenda do dia
    static final int DIAS_A_FRENTE = 7;

    private final MedicoRepository medicoRepository;
    private final GestanteRepository gestanteRepository;
    private final AgendamentoRepository agendamentoRepository;

    public PainelMedicoService(MedicoRepository medicoRepository, GestanteRepository gestanteRepository,
                               AgendamentoRepository agendamentoRepository) {
        this.medicoRepository = medicoRepository;
        this.gestanteRepository = gestanteRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    @Transactional(readOnly = true)
    public PainelMedicoResponse painel(Long idMedico) {
        LocalDate hoje = LocalDate.now();
        Medico medico = medicoRepository.findById(idMedico).orElseThrow();
        // O alcance da agenda do dia (AgendamentoService.listarDeHoje) e da listagem de gestantes
        // (GestanteService.listar): sem a permissão do administrador, só as gestantes que acompanha. Nulo = todas
        Medico alcance = medico.podeVerTodasGestantes() ? null : medico;
        Long idAlcance = alcance == null ? null : alcance.getId();

        return new PainelMedicoResponse(
                gestanteRepository.count(Filtros.igual("medico", alcance)),
                ListaCurtaResponse.de(agendamentoRepository.pendentesAntesDe(idAlcance, hoje, PRIMEIROS)
                        .map(PainelMedicoService::item)),
                ListaCurtaResponse.de(agendamentoRepository.pendentesEntre(idAlcance, hoje.plusDays(1),
                        hoje.plusDays(DIAS_A_FRENTE), PRIMEIROS).map(PainelMedicoService::item)),
                ListaCurtaResponse.de(gestanteRepository.semAgendamentoPendente(idAlcance, PRIMEIROS)
                        .map(g -> new PainelMedicoResponse.GestanteSemPendente(g.getId(), g.getNome()))));
    }

    private static PainelMedicoResponse.AgendamentoPendente item(Agendamento agendamento) {
        return new PainelMedicoResponse.AgendamentoPendente(agendamento.getId(), agendamento.getGestante().getId(),
                agendamento.getGestante().getNome(), agendamento.getTipo().getNome(),
                agendamento.getTipo().getCategoria(), agendamento.getDataPrevista());
    }
}
