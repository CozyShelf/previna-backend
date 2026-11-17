package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.application.service.ResgateService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O contrato de "meus cupons" com o app: filtros da query string, gestante do token, JSON e erros. Quem pode chamar
// cada rota está no MatrizAutorizacaoTest.
@WebMvcTest(CupomResgatadoController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + CupomResgatadoControllerTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class CupomResgatadoControllerTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private ResgateService resgateService;

    private Gestante gestante(Long id) {
        return Campos.definir(new Gestante("Maria", "12345678909", null, "12345678909", "hash", null,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()), "id", id);
    }

    private String tokenGestante(Long id) {
        return "Bearer " + jwtService.gerarToken(Campos.definir(gestante(id), "statusConta", StatusConta.ATIVA));
    }

    private static ResgateResponse cupom() {
        return new ResgateResponse(12L, TipoResgate.CUPOM, "AB12CD34", "10% de desconto em Fralda RN",
                new ResgateResponse.Empresa(3L, "Loja X"), "Fralda RN", Categoria.FRALDAS, new BigDecimal("10.00"),
                LocalDate.of(2026, 12, 31), StatusCupomResgatado.ATIVO, Instant.parse("2026-11-16T22:10:00Z"), null,
                120);
    }

    private static ResgateResponse promocao() {
        return new ResgateResponse(13L, TipoResgate.PROMOCAO, "EF56GH78", "Semana do bebê",
                new ResgateResponse.Empresa(3L, "Loja X"), null, Categoria.ROUPAS, new BigDecimal("15.00"),
                LocalDate.of(2026, 11, 20), StatusCupomResgatado.UTILIZADO, Instant.parse("2026-11-10T13:00:00Z"),
                Instant.parse("2026-11-11T15:30:00Z"), 0);
    }

    @Test
    void listaComOsFiltrosDaQueryEAGestanteDoToken() throws Exception {
        when(resgateService.listar(eq(7L), any())).thenReturn(new PaginaResponse<>(List.of(cupom(), promocao()),
                1, 5, 7, 2));

        mvc.perform(get("/gestantes/cupons").header("Authorization", tokenGestante(7L))
                        .param("busca", "fralda").param("categoria", "FRALDAS").param("tipo", "CUPOM")
                        .param("status", "ATIVO").param("pagina", "1").param("tamanho", "5")
                        .param("idGestante", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(12))
                .andExpect(jsonPath("$.itens[0].tipo").value("CUPOM"))
                .andExpect(jsonPath("$.itens[0].codigo").value("AB12CD34"))
                .andExpect(jsonPath("$.itens[0].titulo").value("10% de desconto em Fralda RN"))
                .andExpect(jsonPath("$.itens[0].empresa.id").value(3))
                .andExpect(jsonPath("$.itens[0].empresa.nome").value("Loja X"))
                .andExpect(jsonPath("$.itens[0].produto").value("Fralda RN"))
                .andExpect(jsonPath("$.itens[0].categoria").value("FRALDAS"))
                .andExpect(jsonPath("$.itens[0].desconto").value(10.00))
                .andExpect(jsonPath("$.itens[0].dataValidade").value("2026-12-31"))
                .andExpect(jsonPath("$.itens[0].status").value("ATIVO"))
                .andExpect(jsonPath("$.itens[0].dataResgate").value("2026-11-16T22:10:00Z"))
                // Ao contrário do resto da API, os nulos vão no JSON, como no contrato com o app
                .andExpect(jsonPath("$.itens[0].dataUso").value(nullValue()))
                .andExpect(jsonPath("$.itens[0].pontosGastos").value(120))
                .andExpect(jsonPath("$.itens[1].produto").value(nullValue()))
                .andExpect(jsonPath("$.itens[1].pontosGastos").value(0))
                .andExpect(jsonPath("$.itens[1].dataUso").value("2026-11-11T15:30:00Z"))
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        verify(resgateService).listar(7L, new ResgateRequest("fralda", Categoria.FRALDAS, TipoResgate.CUPOM,
                StatusCupomResgatado.ATIVO, 1, 5));
    }

    @Test
    void semFiltrosChegaTudoNulo() throws Exception {
        when(resgateService.listar(eq(7L), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/gestantes/cupons").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());

        verify(resgateService).listar(7L, new ResgateRequest(null, null, null, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoria=BRINQUEDOS", "tipo=BRINDE", "status=CANCELADO", "tamanho=101", "tamanho=0",
            "pagina=-1"})
    void filtroInvalidoResponde400SemChegarAoServico(String filtro) throws Exception {
        mvc.perform(get("/gestantes/cupons?" + filtro).header("Authorization", tokenGestante(7L)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(resgateService);
    }

    @Test
    void buscaComMaisDeCemCaracteresResponde400() throws Exception {
        mvc.perform(get("/gestantes/cupons").param("busca", "a".repeat(101))
                        .header("Authorization", tokenGestante(7L)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(resgateService);
    }

    @Test
    void detalheComAGestanteDoToken() throws Exception {
        when(resgateService.detalhar(7L, 13L)).thenReturn(promocao());

        mvc.perform(get("/gestantes/cupons/13").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PROMOCAO"))
                .andExpect(jsonPath("$.titulo").value("Semana do bebê"))
                .andExpect(jsonPath("$.categoria").value("ROUPAS"))
                .andExpect(jsonPath("$.status").value("UTILIZADO"))
                .andExpect(jsonPath("$.produto").value(nullValue()));
    }

    @Test
    void detalheDeResgateInexistenteOuDeOutraGestanteResponde404() throws Exception {
        when(resgateService.detalhar(7L, 99L)).thenThrow(new NaoEncontradoException("Resgate não encontrado"));

        mvc.perform(get("/gestantes/cupons/99").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resgate não encontrado"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "99999999999999999999"})
    void idInvalidoNoCaminhoNaoChegaAoServico(String id) throws Exception {
        mvc.perform(get("/gestantes/cupons/{id}", id).header("Authorization", tokenGestante(7L)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(resgateService);
    }

    @Test
    void tokenDePrimeiroAcessoPendenteRecebe403() throws Exception {
        // Ainda com a senha temporária: o token só serve para concluir o primeiro acesso
        String temporario = "Bearer " + jwtService.gerarToken(gestante(7L));

        mvc.perform(get("/gestantes/cupons").header("Authorization", temporario)).andExpect(status().isForbidden());
        mvc.perform(get("/gestantes/cupons/1").header("Authorization", temporario)).andExpect(status().isForbidden());
        verifyNoInteractions(resgateService);
    }

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get("/gestantes/cupons")).andExpect(status().isUnauthorized());
        mvc.perform(get("/gestantes/cupons/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(resgateService);
    }
}
