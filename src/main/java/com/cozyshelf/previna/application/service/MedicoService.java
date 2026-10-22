package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.response.PerfilMedicoResponse;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicoService {
    private final MedicoRepository medicoRepository;

    public MedicoService(MedicoRepository medicoRepository) {
        this.medicoRepository = medicoRepository;
    }

    @Transactional(readOnly = true)
    public PerfilMedicoResponse buscarPerfil(Long idMedico) {
        Medico medico = medicoRepository.findById(idMedico).orElseThrow();
        return new PerfilMedicoResponse(medico.getNome(), medico.getEmail(), medico.getLogin(), medico.getCrm());
    }
}
