package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.TipoAtendimentoRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.TipoAtendimentoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Catálogo de exames e consultas. Cada médico cadastra os seus; os tipos da migração V11 são os padrão,
// visíveis para todos e editáveis por ninguém.
@Service
public class TipoAtendimentoService {
    private final TipoAtendimentoRepository tipoRepository;

    public TipoAtendimentoService(TipoAtendimentoRepository tipoRepository) {
        this.tipoRepository = tipoRepository;
    }

    // O que o médico pode escolher num agendamento: os padrão mais os dele, sem os excluídos.
    // A categoria filtra aqui, e não no banco, porque a consulta já devolve o catálogo inteiro do médico.
    @Transactional(readOnly = true)
    public List<TipoAtendimentoResponse> disponiveis(Long idMedico, CategoriaAtendimento categoria) {
        return tipoRepository.disponiveisPara(idMedico).stream()
                .filter(tipo -> categoria == null || tipo.getCategoria() == categoria)
                .map(TipoAtendimentoService::resposta)
                .toList();
    }

    // A gestão mostra só os tipos vivos do próprio médico: os padrão não são dele para editar
    @Transactional(readOnly = true)
    public PaginaResponse<TipoAtendimentoResponse> listar(Long idMedico, ListagemRequest listagem,
                                                          CategoriaAtendimento categoria) {
        Specification<TipoAtendimento> filtro = Specification.allOf(
                Filtros.igual("criadoPor", idMedico),
                Filtros.igual("ativo", true),
                Filtros.igual("categoria", categoria),
                Filtros.busca(listagem.busca(), null, "nome"));
        return PaginaResponse.de(tipoRepository.findAll(filtro, listagem.ordenadaPor("nome"))
                .map(TipoAtendimentoService::resposta));
    }

    @Transactional
    public TipoAtendimentoResponse cadastrar(Long idMedico, TipoAtendimentoRequest dados) {
        String nome = dados.nome().trim();
        if (tipoRepository.existsByNomeAndCriadoPorAndAtivoTrue(nome, idMedico)) {
            throw new ConflitoException("Você já tem um tipo com esse nome");
        }
        return resposta(tipoRepository.save(
                new TipoAtendimento(idMedico, nome, dados.categoria(), dados.pontosBase())));
    }

    @Transactional(readOnly = true)
    public TipoAtendimentoResponse buscar(Long idMedico, Long idTipo) {
        return resposta(encontrar(idMedico, idTipo));
    }

    @Transactional
    public TipoAtendimentoResponse atualizar(Long idMedico, Long idTipo, TipoAtendimentoRequest dados) {
        TipoAtendimento tipo = encontrar(idMedico, idTipo);
        String nome = dados.nome().trim();
        if (!nome.equals(tipo.getNome())
                && tipoRepository.existsByNomeAndCriadoPorAndAtivoTrue(nome, idMedico)) {
            throw new ConflitoException("Você já tem um tipo com esse nome");
        }
        tipo.atualizar(nome, dados.categoria(), dados.pontosBase());
        return resposta(tipo);
    }

    // Exclusão lógica: apagar levaria junto o nome do que a gestante já fez, e o agendamento tem chave
    // estrangeira para cá. O tipo some da gestão e da lista de escolha, e o histórico continua legível.
    @Transactional
    public void excluir(Long idMedico, Long idTipo) {
        encontrar(idMedico, idTipo).excluir();
    }

    // Tipo escolhido no cadastro de um agendamento: não pode estar excluído e precisa ser visível para este médico
    @Transactional(readOnly = true)
    public TipoAtendimento paraAgendamento(Long idMedico, Long idTipo) {
        return tipoRepository.findById(idTipo)
                .filter(TipoAtendimento::isAtivo)
                .filter(tipo -> tipo.visivelPara(idMedico))
                .orElseThrow(() -> new DadosInvalidosException("Tipo de atendimento inválido"));
    }

    private TipoAtendimento encontrar(Long idMedico, Long idTipo) {
        return tipoRepository.findByIdAndCriadoPorAndAtivoTrue(idTipo, idMedico)
                .orElseThrow(() -> new NaoEncontradoException("Tipo de atendimento não encontrado"));
    }

    static TipoAtendimentoResponse resposta(TipoAtendimento tipo) {
        return new TipoAtendimentoResponse(tipo.getId(), tipo.getNome(), tipo.getCategoria(),
                tipo.getPontosBase(), tipo.padrao());
    }
}
