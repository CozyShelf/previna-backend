package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ConteudoRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.ConteudoInformativo;
import com.cozyshelf.previna.domain.model.ImagemConteudo;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import com.cozyshelf.previna.domain.repository.ConteudoInformativoRepository;
import com.cozyshelf.previna.domain.repository.ImagemConteudoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConteudoServiceTest {
    private final ConteudoInformativoRepository conteudoRepository = mock(ConteudoInformativoRepository.class);
    private final ImagemConteudoRepository imagemRepository = mock(ImagemConteudoRepository.class);
    private final ConteudoService service = new ConteudoService(conteudoRepository, imagemRepository);

    private static final ConteudoRequest TEXTO = new ConteudoRequest("Alimentação na gestação",
            CategoriaConteudo.DICA, TipoConteudo.TEXTO, "Coma de tudo um pouco", "Corpo do artigo", null);
    private static final ConteudoRequest LINK = new ConteudoRequest("Calendário de vacinação",
            CategoriaConteudo.NOTICIA, TipoConteudo.LINK, null, null, "https://www.gov.br/saude");
    private static final ConteudoRequest FOTO = new ConteudoRequest("Mala da maternidade",
            CategoriaConteudo.DICA, TipoConteudo.FOTO, null, null, null);

    private static MockMultipartFile arquivo(String hex) {
        return new MockMultipartFile("imagem", "imagem", "image/png", HexFormat.of().parseHex(hex));
    }

    private static final String PNG = "89504e470d0a1a0a";

    // O banco gera o id ao salvar
    private void salvarComId(long id) {
        when(conteudoRepository.save(any())).thenAnswer(i -> {
            ConteudoInformativo conteudo = i.getArgument(0);
            ReflectionTestUtils.setField(conteudo, "id", id);
            return conteudo;
        });
    }

    @Test
    void cadastraComoRascunhoEComOAdminDoTokenComoAutor() {
        salvarComId(5L);

        var resposta = service.cadastrar(9L, TEXTO, null);

        var salvo = ArgumentCaptor.forClass(ConteudoInformativo.class);
        verify(conteudoRepository).save(salvo.capture());
        assertEquals(9L, salvo.getValue().getAutorId());
        assertFalse(resposta.publicado());
        assertNull(resposta.publicadoEm());
        assertNotNull(resposta.criadoEm());
        verify(imagemRepository, never()).save(any());
    }

    @Test
    void guardaImagemEnviadaEApontaMidiaParaARotaDaApi() {
        salvarComId(5L);

        var resposta = service.cadastrar(9L, FOTO, arquivo(PNG));

        var imagem = ArgumentCaptor.forClass(ImagemConteudo.class);
        verify(imagemRepository).save(imagem.capture());
        assertEquals("image/png", imagem.getValue().getTipo());
        assertTrue(resposta.midiaUrl().startsWith("/conteudos/5/imagem?v="));
    }

    @Test
    void recusaArquivoQueNaoEImagemSemSalvarOConteudo() {
        // SVG declarado como PNG pelo navegador
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(9L, FOTO, arquivo("3c73766720786d6c6e733d")));
        verify(conteudoRepository, never()).save(any());
    }

    // Cada tipo tem o seu campo obrigatório: sem ele o app não teria o que mostrar
    @Test
    void recusaTextoSemCorpo() {
        var semCorpo = new ConteudoRequest("Título", CategoriaConteudo.DICA, TipoConteudo.TEXTO, null, "   ", null);

        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(9L, semCorpo, null));
        verify(conteudoRepository, never()).save(any());
    }

    @Test
    void recusaVideoSemMidia() {
        var semMidia = new ConteudoRequest("Título", CategoriaConteudo.DICA, TipoConteudo.VIDEO, null, "Corpo", null);

        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(9L, semMidia, null));
    }

    @Test
    void recusaFotoSemArquivoESemEndereco() {
        assertThrows(DadosInvalidosException.class, () -> service.cadastrar(9L, FOTO, null));
    }

    @Test
    void aceitaFotoComEnderecoExternoSemArquivo() {
        salvarComId(5L);
        var externa = new ConteudoRequest("Mala da maternidade", CategoriaConteudo.DICA, TipoConteudo.FOTO,
                null, null, "https://exemplo.com/foto.png");

        var resposta = service.cadastrar(9L, externa, null);

        assertEquals("https://exemplo.com/foto.png", resposta.midiaUrl());
        verify(imagemRepository, never()).save(any());
    }

    // Opcional em branco fica nulo no banco, não texto vazio
    @Test
    void campoOpcionalEmBrancoViraNulo() {
        salvarComId(5L);
        var comBrancos = new ConteudoRequest("  Título  ", CategoriaConteudo.DICA, TipoConteudo.TEXTO, "  ",
                "Corpo", "   ");

        var resposta = service.cadastrar(9L, comBrancos, null);

        assertEquals("Título", resposta.titulo());
        assertNull(resposta.resumo());
        assertNull(resposta.midiaUrl());
    }

    @Test
    void publicarGravaADataEDespublicarALimpa() {
        var conteudo = novo(5L, TipoConteudo.TEXTO, null);
        when(conteudoRepository.findById(5L)).thenReturn(Optional.of(conteudo));

        var publicada = service.alterarPublicacao(5L, true);
        assertTrue(publicada.publicado());
        assertNotNull(publicada.publicadoEm());

        var despublicada = service.alterarPublicacao(5L, false);
        assertFalse(despublicada.publicado());
        assertNull(despublicada.publicadoEm());
    }

    @Test
    void alterarPublicacaoDeConteudoInexistenteDaNaoEncontrado() {
        when(conteudoRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.alterarPublicacao(5L, true));
    }

    @Test
    void atualizaMantendoAFotoJaSalvaQuandoNenhumaNovaEEnviada() {
        var conteudo = novo(5L, TipoConteudo.FOTO, "/conteudos/5/imagem?v=1");
        when(conteudoRepository.findById(5L)).thenReturn(Optional.of(conteudo));

        var resposta = service.atualizar(5L, FOTO, null);

        assertEquals("/conteudos/5/imagem?v=1", resposta.midiaUrl());
        verify(imagemRepository, never()).save(any());
    }

    // Trocar o tipo para fora de FOTO abandona os bytes da imagem
    @Test
    void sairDeFotoApagaAImagemGuardada() {
        var conteudo = novo(5L, TipoConteudo.FOTO, "/conteudos/5/imagem?v=1");
        when(conteudoRepository.findById(5L)).thenReturn(Optional.of(conteudo));

        var resposta = service.atualizar(5L, LINK, null);

        assertEquals(TipoConteudo.LINK, resposta.tipo());
        assertEquals("https://www.gov.br/saude", resposta.midiaUrl());
        verify(imagemRepository).deleteAllById(List.of(5L));
    }

    @Test
    void atualizarConteudoInexistenteDaNaoEncontrado() {
        when(conteudoRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.atualizar(5L, TEXTO, null));
    }

    // Rascunho e inexistente respondem igual para a gestante: 404, sem dizer qual dos dois
    @Test
    void gestanteNaoAlcancaRascunho() {
        when(conteudoRepository.findByIdAndPublicadoTrue(5L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.buscarPublicado(5L));
        verify(conteudoRepository, never()).findById(any());
    }

    @Test
    void listagemDaGestanteUsaASementeRecebida() {
        when(conteudoRepository.publicadosEmOrdemAleatoria(eq("abc123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(novo(1L, TipoConteudo.TEXTO, null))));

        var pagina = service.listarPublicados(new ListagemRequest(null, 0, 5), "abc123");

        assertEquals(1, pagina.itens().size());
        var paginavel = ArgumentCaptor.forClass(Pageable.class);
        verify(conteudoRepository).publicadosEmOrdemAleatoria(eq("abc123"), paginavel.capture());
        assertEquals(5, paginavel.getValue().getPageSize());
        // A ordem vem da consulta nativa; um Sort aqui seria acrescentado depois e desfaria o sorteio
        assertTrue(paginavel.getValue().getSort().isUnsorted());
    }

    @Test
    void semSementeAListagemDaGestanteUsaOrdemEstavel() {
        when(conteudoRepository.publicadosEmOrdemAleatoria(eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listarPublicados(new ListagemRequest(null, null, null), null);

        verify(conteudoRepository).publicadosEmOrdemAleatoria(eq(""), any(Pageable.class));
    }

    @Test
    void excluirConteudoInexistenteDaNaoEncontrado() {
        when(conteudoRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.excluir(5L));
        // O tipo explícito desfaz a ambiguidade com delete(Specification) do JpaSpecificationExecutor
        verify(conteudoRepository, never()).delete(any(ConteudoInformativo.class));
    }

    private static ConteudoInformativo novo(long id, TipoConteudo tipo, String midiaUrl) {
        var conteudo = new ConteudoInformativo(9L, "Título", CategoriaConteudo.DICA, tipo, null, "Corpo", midiaUrl);
        ReflectionTestUtils.setField(conteudo, "id", id);
        return conteudo;
    }
}
