package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// "Meus cupons" da gestante: os cupons e as promoções que ela resgatou, com o código para mostrar na loja.
// O status EXPIRADO é gravado na leitura: antes de listar (ou detalhar), os ATIVOS vencidos viram EXPIRADO com um
// UPDATE condicionado a ATIVO (ver CupomResgatadoRepository.EXPIRAR). Por isso as transações não são readOnly.
@Service
public class ResgateService {
    static final String RESGATE_NAO_ENCONTRADO = "Resgate não encontrado";

    private final CupomResgatadoRepository resgatadoRepository;

    public ResgateService(CupomResgatadoRepository resgatadoRepository) {
        this.resgatadoRepository = resgatadoRepository;
    }

    // ATIVO primeiro, depois UTILIZADO e EXPIRADO juntos, cada grupo do resgate mais recente para o mais antigo
    @Transactional
    public PaginaResponse<ResgateResponse> listar(Long idGestante, ResgateRequest filtros) {
        resgatadoRepository.expirarVencidos(idGestante, LocalDate.now());
        return PaginaResponse.de(resgatadoRepository.daGestante(idGestante, filtros.tipo(), filtros.status(),
                filtros.categoria(), filtros.padraoBusca(), filtros.paginacao()).map(ResgateResponse::de));
    }

    // Resgate de outra gestante responde como inexistente, para não revelar os ids dos resgates dos outros
    @Transactional
    public ResgateResponse detalhar(Long idGestante, Long idResgate) {
        resgatadoRepository.expirarSeVencido(idResgate, idGestante, LocalDate.now());
        return resgatadoRepository.daGestante(idResgate, idGestante).map(ResgateResponse::de)
                .orElseThrow(() -> new NaoEncontradoException(RESGATE_NAO_ENCONTRADO));
    }
}
