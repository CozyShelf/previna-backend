package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.OfertaRequest;
import com.cozyshelf.previna.application.dto.response.OfertaEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaDetalheResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaResumoResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.application.service.OfertaService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.TipoOferta;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O contrato do catálogo com o app: filtros da query string, gestante do token, JSON e erros. Quem pode chamar cada
// rota está no MatrizAutorizacaoTest.
@WebMvcTest(OfertaController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + OfertaControllerTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class OfertaControllerTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private OfertaService ofertaService;

    private String tokenGestante(Long id) {
        var gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash", null,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());
        return "Bearer " + jwtService.gerarToken(Campos.definir(Campos.definir(gestante, "id", id),
                "statusConta", StatusConta.ATIVA));
    }

    private static ProdutoOfertaResumoResponse soPromocao() {
        return new ProdutoOfertaResumoResponse(5L, "Fralda RN", Categoria.FRALDAS, null, null, null,
                new BigDecimal("15.00"));
    }

    @Test
    void listaComOsFiltrosDaQueryEAGestanteDoToken() throws Exception {
        when(ofertaService.listarEmpresas(eq(7L), any())).thenReturn(new PaginaResponse<>(List.of(
                new OfertaEmpresaResponse(1L, "Loja X", "Farmácia", null, 14, List.of(soPromocao()))), 1, 5, 6, 2));

        mvc.perform(get("/ofertas").header("Authorization", tokenGestante(7L))
                        .param("busca", "fralda").param("categoria", "FRALDAS").param("empresaId", "1")
                        .param("tipo", "PROMOCAO").param("ateMeusPontos", "true")
                        .param("pagina", "1").param("tamanho", "5")
                        .param("idGestante", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Loja X"))
                .andExpect(jsonPath("$.itens[0].totalProdutos").value(14))
                .andExpect(jsonPath("$.itens[0].produtos[0].descontoPromocao").value(15.00))
                // Ao contrário do resto da API, os nulos vão no JSON: o app sabe que não há selo nem logo
                .andExpect(jsonPath("$.itens[0].logo").value(nullValue()))
                .andExpect(jsonPath("$.itens[0].produtos[0].menorPontos").value(nullValue()))
                .andExpect(jsonPath("$.itens[0].produtos[0].maiorDescontoCupom").value(nullValue()))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        var filtros = ArgumentCaptor.forClass(OfertaRequest.class);
        verify(ofertaService).listarEmpresas(eq(7L), filtros.capture());
        assertEquals(new OfertaRequest("fralda", Categoria.FRALDAS, 1L, TipoOferta.PROMOCAO, true, 1, 5),
                filtros.getValue());
    }

    @Test
    void semFiltrosChegaTudoNulo() throws Exception {
        when(ofertaService.listarEmpresas(eq(7L), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 10, 0, 0));

        mvc.perform(get("/ofertas").header("Authorization", tokenGestante(7L))).andExpect(status().isOk());

        verify(ofertaService).listarEmpresas(7L, new OfertaRequest(null, null, null, null, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoria=BRINQUEDOS", "tipo=BRINDE", "tamanho=101", "tamanho=0", "pagina=-1",
            "empresaId=abc", "ateMeusPontos=talvez"})
    void filtroInvalidoResponde400SemChegarAoServico(String filtro) throws Exception {
        mvc.perform(get("/ofertas?" + filtro).header("Authorization", tokenGestante(7L)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/ofertas/empresas/1/produtos?" + filtro).header("Authorization", tokenGestante(7L)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ofertaService);
    }

    @Test
    void buscaComMaisDeCemCaracteresResponde400() throws Exception {
        mvc.perform(get("/ofertas").param("busca", "a".repeat(101)).header("Authorization", tokenGestante(7L)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ofertaService);
    }

    @Test
    void verTodosUsaAEmpresaDoCaminho() throws Exception {
        when(ofertaService.listarProdutos(eq(7L), eq(3L), any()))
                .thenReturn(new PaginaResponse<>(List.of(soPromocao()), 0, 10, 1, 1));

        mvc.perform(get("/ofertas/empresas/3/produtos").param("tipo", "CUPOM")
                        .header("Authorization", tokenGestante(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(5))
                .andExpect(jsonPath("$.itens[0].imagem").value(nullValue()));

        verify(ofertaService).listarProdutos(eq(7L), eq(3L), any());
    }

    @Test
    void verTodosDeEmpresaInexistenteResponde404() throws Exception {
        when(ofertaService.listarProdutos(eq(7L), eq(99L), any()))
                .thenThrow(new NaoEncontradoException("Empresa não encontrada"));

        mvc.perform(get("/ofertas/empresas/99/produtos").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Empresa não encontrada"));
    }

    @Test
    void detalheDoProduto() throws Exception {
        when(ofertaService.detalhar(7L, 5L)).thenReturn(new ProdutoOfertaDetalheResponse(5L, "Fralda RN",
                Categoria.FRALDAS, "/empresas/produtos/5/imagem?v=1",
                new ProdutoOfertaDetalheResponse.Empresa(1L, "Loja X", null, null),
                List.of(new ProdutoOfertaDetalheResponse.Cupom(9L, new BigDecimal("10.00"), 120,
                        LocalDate.of(2026, 12, 31))),
                List.of(new ProdutoOfertaDetalheResponse.Promocao(3L, "Semana do bebê", new BigDecimal("15.00"),
                        LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), true))));

        mvc.perform(get("/ofertas/produtos/5").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.nome").value("Loja X"))
                .andExpect(jsonPath("$.empresa.logo").value(nullValue()))
                .andExpect(jsonPath("$.cupons[0].pontosNecessarios").value(120))
                .andExpect(jsonPath("$.cupons[0].dataValidade").value("2026-12-31"))
                .andExpect(jsonPath("$.promocoes[0].resgatada").value(true));
    }

    @Test
    void detalheDeProdutoSemOfertaResponde404() throws Exception {
        when(ofertaService.detalhar(7L, 5L)).thenThrow(new NaoEncontradoException("Produto não encontrado"));

        mvc.perform(get("/ofertas/produtos/5").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Produto não encontrado"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    void idInvalidoNoCaminhoNaoChegaAoServico(String id) throws Exception {
        mvc.perform(get("/ofertas/produtos/{id}", id).header("Authorization", tokenGestante(7L)))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/ofertas/empresas/{id}/produtos", id).header("Authorization", tokenGestante(7L)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(ofertaService);
    }
}
