package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ProdutoRequest;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.ImagemProduto;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.repository.ImagemProdutoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProdutoServiceTest {
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final ImagemProdutoRepository imagemRepository = mock(ImagemProdutoRepository.class);
    private final ProdutoService service = new ProdutoService(produtoRepository, imagemRepository);

    private static final ProdutoRequest DADOS = new ProdutoRequest("Fralda RN", "Pacote com 36", Categoria.FRALDAS,
            new BigDecimal("39.90"));

    private static MockMultipartFile arquivo(String hex) {
        return new MockMultipartFile("imagem", "imagem", "image/png", HexFormat.of().parseHex(hex));
    }

    // O banco gera o id ao salvar
    private void salvarComId(long id) {
        when(produtoRepository.save(any())).thenAnswer(i -> {
            Produto produto = i.getArgument(0);
            ReflectionTestUtils.setField(produto, "id", id);
            return produto;
        });
    }

    @Test
    void cadastraNaEmpresaDoTokenSemImagem() {
        salvarComId(5L);

        var resposta = service.cadastrar(1L, DADOS, null);

        var salvo = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository).save(salvo.capture());
        assertEquals(1L, salvo.getValue().getEmpresaId());
        assertEquals(new BigDecimal("39.90"), resposta.preco());
        assertNull(resposta.imagemUrl());
        verify(imagemRepository, never()).save(any());
    }

    @Test
    void guardaImagemEnviada() {
        salvarComId(5L);

        var resposta = service.cadastrar(1L, DADOS, arquivo("89504e470d0a1a0a"));

        var imagem = ArgumentCaptor.forClass(ImagemProduto.class);
        verify(imagemRepository).save(imagem.capture());
        assertEquals("image/png", imagem.getValue().getTipo());
        assertTrue(resposta.imagemUrl().startsWith("/empresas/produtos/5/imagem?v="));
    }

    @Test
    void recusaArquivoQueNaoEImagemSemSalvarOProduto() {
        // SVG declarado como PNG pelo navegador
        assertThrows(DadosInvalidosException.class,
                () -> service.cadastrar(1L, DADOS, arquivo("3c73766720786d6c6e733d")));
        verify(produtoRepository, never()).save(any());
    }

    @Test
    void atualizaOsDadosEMantemAImagemQuandoNaoVemOutra() {
        var produto = new Produto(1L, "Fralda RN", null, Categoria.FRALDAS, new BigDecimal("39.90"));
        produto.alterarImagem("/empresas/produtos/5/imagem?v=1");
        when(produtoRepository.findByIdAndEmpresaId(5L, 1L)).thenReturn(Optional.of(produto));

        var resposta = service.atualizar(1L, 5L, new ProdutoRequest("Fralda P", null, null, new BigDecimal("42.00")), null);

        assertEquals("Fralda P", resposta.nome());
        assertNull(resposta.categoria());
        assertEquals(new BigDecimal("42.00"), resposta.preco());
        assertEquals("/empresas/produtos/5/imagem?v=1", resposta.imagemUrl());
        verify(imagemRepository, never()).save(any());
    }

    @Test
    void naoAtualizaProdutoDeOutraEmpresa() {
        when(produtoRepository.findByIdAndEmpresaId(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.atualizar(2L, 5L, DADOS, null));
    }
}
