package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.response.TipoAtendimentoResponse;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgendamentoService {
    private final TipoAtendimentoRepository tipoRepository;

    public AgendamentoService(TipoAtendimentoRepository tipoRepository) {
        this.tipoRepository = tipoRepository;
    }

    // Só os tipos ativos: um tipo desativado não pode mais ser escolhido, mas continua nos agendamentos antigos
    @Transactional(readOnly = true)
    public List<TipoAtendimentoResponse> listarTiposAtivos(CategoriaAtendimento categoria) {
        List<TipoAtendimento> tipos = categoria == null
                ? tipoRepository.findByAtivoTrueOrderByNomeAsc()
                : tipoRepository.findByCategoriaAndAtivoTrueOrderByNomeAsc(categoria);
        return tipos.stream().map(AgendamentoService::resposta).toList();
    }

    private static TipoAtendimentoResponse resposta(TipoAtendimento tipo) {
        return new TipoAtendimentoResponse(tipo.getId(), tipo.getNome(), tipo.getCategoria(), tipo.getPontosBase());
    }
}
