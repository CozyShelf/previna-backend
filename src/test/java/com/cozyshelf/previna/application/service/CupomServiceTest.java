package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CupomServiceTest {
    private static final LocalDate VALIDADE = LocalDate.now().plusDays(30);

    private final CupomRepository cupomRepository = mock(CupomRepository.class);
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final CupomService service = new CupomService(cupomRepository, produtoRepository);

    private final Produto fralda = new Produto(1L, "Fralda RN", null, Categoria.FRALDAS, new BigDecimal("39.90"));

    private static CupomRequest dados(Long produto, String desconto) {
        return new CupomRequest(produto, new BigDecimal(desconto), VALIDADE, 200);
    }

    @Test
    void cadastraParaUmProdutoDaEmpresaAtivoEComADescricaoMontada() {
        when(produtoRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(fralda));
        when(cupomRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resposta = service.cadastrar(1L, dados(10L, "12.50"));

        var salvo = ArgumentCaptor.forClass(Cupom.class);
        verify(cupomRepository).save(salvo.capture());
        assertSame(fralda, salvo.getValue().getProduto());
        assertEquals("12,5% de desconto em Fralda RN", resposta.descricao());
        assertEquals("Fralda RN", resposta.produto());
        assertTrue(resposta.ativo());
    }

    @Test
    void descricaoSemCasasDecimaisQuandoODescontoEInteiro() {
        when(produtoRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(fralda));
        when(cupomRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("100% de desconto em Fralda RN", service.cadastrar(1L, dados(10L, "100.00")).descricao());
    }

    @Test
    void recusaProdutoDeOutraEmpresa() {
        // O produto 99 é de outra empresa: a busca filtrada pela empresa do token não o acha
        when(produtoRepository.findByIdAndEmpresaId(99L, 1L)).thenReturn(Optional.empty());

        var erro = assertThrows(DadosInvalidosException.class, () -> service.cadastrar(1L, dados(99L, "10")));
        assertEquals("Produto não pertence à empresa", erro.getMessage());
        verify(cupomRepository, never()).save(any());
    }

    @Test
    void desativaEReativaCupomDaEmpresa() {
        var cupom = new Cupom(1L, fralda, BigDecimal.TEN, VALIDADE, 200);
        when(cupomRepository.findByIdAndEmpresaId(5L, 1L)).thenReturn(Optional.of(cupom));

        assertFalse(service.alterarAtivo(1L, 5L, false).ativo());
        assertTrue(service.alterarAtivo(1L, 5L, true).ativo());
    }

    @Test
    void naoAlteraCupomDeOutraEmpresa() {
        when(cupomRepository.findByIdAndEmpresaId(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.alterarAtivo(2L, 5L, false));
    }
}
