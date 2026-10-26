package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PromocaoResponse;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromocaoService {
    private final PromocaoRepository promocaoRepository;

    public PromocaoService(PromocaoRepository promocaoRepository) {
        this.promocaoRepository = promocaoRepository;
    }

    @Transactional
    public PromocaoResponse cadastrar(Long idEmpresa, PromocaoRequest dados) {
        if (dados.dataFim().isBefore(dados.dataInicio())) {
            throw new DadosInvalidosException("A data de fim deve ser igual ou posterior à de início");
        }
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
        return PaginaResponse.de(promocaoRepository.findAll(filtro, listagem.ordenadaPor("titulo"))
                .map(PromocaoService::resposta));
    }

    private static PromocaoResponse resposta(Promocao promocao) {
        return new PromocaoResponse(promocao.getId(), promocao.getTitulo(), promocao.getDescricao(),
                promocao.getDesconto(), promocao.getDataInicio(), promocao.getDataFim(), promocao.getCategoria());
    }
}
