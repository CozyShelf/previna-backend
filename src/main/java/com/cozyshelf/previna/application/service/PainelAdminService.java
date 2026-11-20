package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.response.ListaCurtaResponse;
import com.cozyshelf.previna.application.dto.response.PainelAdminResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.repository.ConteudoInformativoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.LogAuditoriaRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Painel da tela Início do admin: contagens globais, as empresas que ainda não fizeram o primeiro acesso e o log de
// auditoria recente. Das gestantes, só as duas contagens: nenhum nome nem id sai daqui. Só leitura
@Service
public class PainelAdminService {
    static final int ATIVIDADES = 10;
    // Da pré-cadastrada há mais tempo; o id desempata
    private static final Sort MAIS_ANTIGAS = Sort.by("criadoEm", "id");

    private final MedicoRepository medicoRepository;
    private final EmpresaParceiraRepository empresaRepository;
    private final GestanteRepository gestanteRepository;
    private final ConteudoInformativoRepository conteudoRepository;
    private final LogAuditoriaRepository logRepository;

    public PainelAdminService(MedicoRepository medicoRepository, EmpresaParceiraRepository empresaRepository,
                              GestanteRepository gestanteRepository, ConteudoInformativoRepository conteudoRepository,
                              LogAuditoriaRepository logRepository) {
        this.medicoRepository = medicoRepository;
        this.empresaRepository = empresaRepository;
        this.gestanteRepository = gestanteRepository;
        this.conteudoRepository = conteudoRepository;
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public PainelAdminResponse painel() {
        return new PainelAdminResponse(
                medicoRepository.count(),
                new PainelAdminResponse.Contas(empresaRepository.count(),
                        empresaRepository.countByStatusConta(StatusConta.PRIMEIRO_ACESSO_PENDENTE)),
                new PainelAdminResponse.Contas(gestanteRepository.count(),
                        gestanteRepository.countByStatusConta(StatusConta.PRIMEIRO_ACESSO_PENDENTE)),
                new PainelAdminResponse.Conteudos(conteudoRepository.countByPublicado(true),
                        conteudoRepository.countByPublicado(false)),
                ListaCurtaResponse.de(empresaRepository.findByStatusConta(StatusConta.PRIMEIRO_ACESSO_PENDENTE,
                                PageRequest.of(0, ListaCurtaResponse.ITENS, MAIS_ANTIGAS))
                        .map(e -> new PainelAdminResponse.EmpresaPendente(e.getId(), e.getRazaoSocial(),
                                ResgateResponse.instante(e.getCriadoEm())))),
                logRepository.recentes(Limit.of(ATIVIDADES)).stream()
                        .map(a -> new PainelAdminResponse.Atividade(ResgateResponse.instante(a.dataHora()), a.acao(),
                                a.usuarioNome(), a.perfil(), a.pontos()))
                        .toList());
    }
}
