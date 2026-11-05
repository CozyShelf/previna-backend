package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ConteudoRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.response.ConteudoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.ConteudoInformativo;
import com.cozyshelf.previna.domain.model.ImagemConteudo;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import com.cozyshelf.previna.domain.repository.ConteudoInformativoRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.ImagemConteudoRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class ConteudoService {
    private final ConteudoInformativoRepository conteudoRepository;
    private final ImagemConteudoRepository imagemRepository;

    public ConteudoService(ConteudoInformativoRepository conteudoRepository,
                           ImagemConteudoRepository imagemRepository) {
        this.conteudoRepository = conteudoRepository;
        this.imagemRepository = imagemRepository;
    }

    // O autor é o admin do token. Nasce rascunho: publicar é uma ação à parte
    @Transactional
    public ConteudoResponse cadastrar(Long idAdmin, ConteudoRequest dados, MultipartFile imagem) {
        // Valida a imagem antes de salvar o conteúdo
        Optional<Imagem> arquivo = Imagem.de(imagem);
        String midiaUrl = texto(dados.midiaUrl());
        exigirMidiaDoTipo(dados.tipo(), texto(dados.corpo()), midiaUrl, arquivo.isPresent());

        ConteudoInformativo conteudo = conteudoRepository.save(new ConteudoInformativo(idAdmin,
                dados.titulo().trim(), dados.categoria(), dados.tipo(), texto(dados.resumo()),
                texto(dados.corpo()), midiaUrl));
        arquivo.ifPresent(i -> salvarImagem(conteudo, i));
        return resposta(conteudo);
    }

    // Troca todos os dados; a imagem só muda quando uma nova é enviada
    @Transactional
    public ConteudoResponse atualizar(Long idConteudo, ConteudoRequest dados, MultipartFile imagem) {
        Optional<Imagem> arquivo = Imagem.de(imagem);
        ConteudoInformativo conteudo = encontrar(idConteudo);

        String midiaUrl = texto(dados.midiaUrl());
        // Na edição a foto já salva também vale como mídia, mesmo sem arquivo novo
        boolean temFotoSalva = conteudo.getTipo() == TipoConteudo.FOTO && conteudo.getMidiaUrl() != null;
        exigirMidiaDoTipo(dados.tipo(), texto(dados.corpo()), midiaUrl, arquivo.isPresent() || temFotoSalva);

        // Sem mídia nova e sem arquivo, mantém a foto que já estava salva
        String midiaFinal = midiaUrl != null ? midiaUrl : (temFotoSalva ? conteudo.getMidiaUrl() : null);
        conteudo.atualizar(dados.titulo().trim(), dados.categoria(), dados.tipo(), texto(dados.resumo()),
                texto(dados.corpo()), midiaFinal);
        arquivo.ifPresent(i -> salvarImagem(conteudo, i));

        // Sair de FOTO abandona os bytes da imagem; apaga para não deixar lixo no banco
        if (dados.tipo() != TipoConteudo.FOTO) {
            imagemRepository.deleteAllById(List.of(conteudo.getId()));
        }
        return resposta(conteudo);
    }

    // Publicar ou despublicar. Despublicar tira o conteúdo do app sem apagá-lo
    @Transactional
    public ConteudoResponse alterarPublicacao(Long idConteudo, boolean publicado) {
        ConteudoInformativo conteudo = encontrar(idConteudo);
        conteudo.alterarPublicacao(publicado);
        return resposta(conteudo);
    }

    @Transactional
    public void excluir(Long idConteudo) {
        // A imagem sai junto, pelo ON DELETE CASCADE da migração V13
        conteudoRepository.delete(encontrar(idConteudo));
    }

    @Transactional(readOnly = true)
    public ConteudoResponse buscar(Long idConteudo) {
        return resposta(encontrar(idConteudo));
    }

    // Listagem de gestão do admin: rascunhos e publicados, com busca pelo título
    @Transactional(readOnly = true)
    public PaginaResponse<ConteudoResponse> listarParaGestao(ListagemRequest listagem, CategoriaConteudo categoria,
                                                             TipoConteudo tipo, Boolean publicado) {
        Specification<ConteudoInformativo> filtro = Specification.allOf(
                Filtros.igual("categoria", categoria),
                Filtros.igual("tipo", tipo),
                Filtros.igual("publicado", publicado),
                Filtros.busca(listagem.busca(), null, "titulo"));
        return PaginaResponse.de(conteudoRepository.findAll(filtro, listagem.ordenadaPor("titulo"))
                .map(ConteudoService::resposta));
    }

    // O que a gestante vê: só os publicados, em ordem aleatória semeada pelo app a cada carregamento
    @Transactional(readOnly = true)
    public PaginaResponse<ConteudoResponse> listarPublicados(ListagemRequest listagem, String semente) {
        // Sem semente a ordem é estável: quem não sorteia (ex.: um teste) recebe sempre a mesma sequência
        String usada = semente == null || semente.isBlank() ? "" : semente;
        return PaginaResponse.de(conteudoRepository.publicadosEmOrdemAleatoria(usada, listagem.semOrdem())
                .map(ConteudoService::resposta));
    }

    // Conteúdo despublicado ou inexistente responde igual: 404, sem dizer qual dos dois
    @Transactional(readOnly = true)
    public ConteudoResponse buscarPublicado(Long idConteudo) {
        return resposta(conteudoRepository.findByIdAndPublicadoTrue(idConteudo)
                .orElseThrow(() -> new NaoEncontradoException("Conteúdo não encontrado")));
    }

    @Transactional(readOnly = true)
    public Optional<ImagemConteudo> buscarImagem(Long idConteudo) {
        return imagemRepository.findById(idConteudo);
    }

    // TEXTO vive do corpo; FOTO, VIDEO e LINK não têm o que mostrar sem mídia
    private static void exigirMidiaDoTipo(TipoConteudo tipo, String corpo, String midiaUrl, boolean temImagem) {
        if (tipo == TipoConteudo.TEXTO) {
            if (corpo == null) {
                throw new DadosInvalidosException("Informe o corpo do texto");
            }
            return;
        }
        if (tipo == TipoConteudo.FOTO && (temImagem || midiaUrl != null)) {
            return;
        }
        if (tipo != TipoConteudo.FOTO && midiaUrl != null) {
            return;
        }
        throw new DadosInvalidosException(tipo == TipoConteudo.FOTO
                ? "Envie a imagem ou informe o endereço da foto"
                : "Informe o endereço da mídia");
    }

    private ConteudoInformativo encontrar(Long idConteudo) {
        return conteudoRepository.findById(idConteudo)
                .orElseThrow(() -> new NaoEncontradoException("Conteúdo não encontrado"));
    }

    private void salvarImagem(ConteudoInformativo conteudo, Imagem imagem) {
        imagemRepository.save(new ImagemConteudo(conteudo.getId(), imagem.conteudo(), imagem.tipo()));
        // A versão no endereço faz o app buscar a imagem nova em vez da que já exibiu
        conteudo.alterarMidia("/conteudos/%d/imagem?v=%d".formatted(conteudo.getId(), System.currentTimeMillis()));
    }

    // Campo opcional em branco fica nulo no banco, em vez de texto vazio
    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static ConteudoResponse resposta(ConteudoInformativo conteudo) {
        return new ConteudoResponse(conteudo.getId(), conteudo.getTitulo(), conteudo.getCategoria(),
                conteudo.getTipo(), conteudo.getResumo(), conteudo.getCorpo(), conteudo.getMidiaUrl(),
                conteudo.isPublicado(), conteudo.getCriadoEm(), conteudo.getPublicadoEm());
    }
}
