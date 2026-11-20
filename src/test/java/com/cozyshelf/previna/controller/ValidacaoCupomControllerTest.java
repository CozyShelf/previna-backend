package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.ValidacaoCupomResponse;
import com.cozyshelf.previna.application.exception.CodigoErro;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.application.service.ResgateService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O contrato da validação do código com o portal: empresa do token, corpo, JSON da resposta e de cada erro. Quem pode
// chamar a rota está no MatrizAutorizacaoTest; a regra (UPDATE condicionado, expiração), no ResgateServiceTest e no
// ValidacaoCupomHttpIntegracaoTest.
@WebMvcTest(ValidacaoCupomController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + ValidacaoCupomControllerTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class ValidacaoCupomControllerTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private ResgateService resgateService;

    private String token(Usuario usuario, Long id) {
        return "Bearer " + jwtService.gerarToken(Campos.definir(Campos.definir(usuario, "id", id),
                "statusConta", StatusConta.ATIVA));
    }

    private String tokenEmpresa(Long id) {
        return token(new EmpresaParceira("Loja X", "12345678000190", null, null, null, "loja", "hash"), id);
    }

    private static MockHttpServletRequestBuilder validar(String token, String corpo) {
        return post("/empresas/cupons/validar").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    @Test
    void validaComAEmpresaDoTokenERespondeOCupom() throws Exception {
        when(resgateService.validar(3L, "ab12cd34 ")).thenReturn(new ValidacaoCupomResponse(12L, TipoResgate.CUPOM,
                "AB12CD34", null, "Fralda RN", Categoria.FRALDAS, new BigDecimal("10.00"), LocalDate.of(2026, 12, 31),
                Instant.parse("2026-11-16T22:10:00Z"), Instant.parse("2026-11-19T14:05:00Z"), "Maria Souza"));

        // O id no corpo é ignorado: a empresa vem do token
        mvc.perform(validar(tokenEmpresa(3L), "{\"codigo\":\"ab12cd34 \",\"empresa\":99}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":12,"tipo":"CUPOM","codigo":"AB12CD34","titulo":null,"produtoNome":"Fralda RN",
                         "categoria":"FRALDAS","desconto":10.00,"validade":"2026-12-31",
                         "dataResgate":"2026-11-16T22:10:00Z","dataUso":"2026-11-19T14:05:00Z",
                         "gestanteNome":"Maria Souza"}""", true));

        verify(resgateService).validar(3L, "ab12cd34 ");
    }

    @Test
    void promocaoVemComTituloEProdutoNulo() throws Exception {
        when(resgateService.validar(3L, "EF56GH78")).thenReturn(new ValidacaoCupomResponse(13L,
                TipoResgate.PROMOCAO, "EF56GH78", "Semana do bebê", null, Categoria.ROUPAS, new BigDecimal("15.00"),
                LocalDate.of(2026, 11, 30), Instant.parse("2026-11-10T13:00:00Z"),
                Instant.parse("2026-11-19T14:05:00Z"), "Maria Souza"));

        mvc.perform(validar(tokenEmpresa(3L), "{\"codigo\":\"EF56GH78\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PROMOCAO"))
                .andExpect(jsonPath("$.titulo").value("Semana do bebê"))
                // Os nulos vão no JSON
                .andExpect(jsonPath("$.produtoNome").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"codigo\":\"\"}", "{\"codigo\":\"   \"}", "{\"codigo\":null}"})
    void codigoVazioResponde400SemChamarOServico(String corpo) throws Exception {
        mvc.perform(validar(tokenEmpresa(3L), corpo)).andExpect(status().isBadRequest());

        verifyNoInteractions(resgateService);
    }

    @Test
    void naoEncontradoResponde404ComOCodigo() throws Exception {
        when(resgateService.validar(any(), any())).thenThrow(new NaoEncontradoException("Código não encontrado",
                CodigoErro.RESGATE_NAO_ENCONTRADO));

        mvc.perform(validar(tokenEmpresa(3L), "{\"codigo\":\"ZZZZ9999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"message":"Código não encontrado","codigo":"RESGATE_NAO_ENCONTRADO"}""", true));
    }

    @Test
    void jaUtilizadoResponde409ComADataDeUso() throws Exception {
        when(resgateService.validar(any(), any())).thenThrow(new ConflitoException("Código já utilizado",
                CodigoErro.RESGATE_JA_UTILIZADO, Instant.parse("2026-11-19T14:05:00Z")));

        mvc.perform(validar(tokenEmpresa(3L), "{\"codigo\":\"AB12CD34\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"message":"Código já utilizado","codigo":"RESGATE_JA_UTILIZADO",
                         "dataUso":"2026-11-19T14:05:00Z"}""", true));
    }

    // Sem data de uso, o campo não aparece (nem "dataUso": null)
    @Test
    void expiradoResponde409SemDataDeUso() throws Exception {
        when(resgateService.validar(any(), any())).thenThrow(new ConflitoException("Código expirado",
                CodigoErro.RESGATE_EXPIRADO));

        mvc.perform(validar(tokenEmpresa(3L), "{\"codigo\":\"AB12CD34\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.dataUso").doesNotExist())
                .andExpect(content().json("""
                        {"message":"Código expirado","codigo":"RESGATE_EXPIRADO"}""", true));
    }

    @Test
    void gestanteRecebe403() throws Exception {
        Gestante gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash", null,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());

        mvc.perform(validar(token(gestante, 7L), "{\"codigo\":\"AB12CD34\"}")).andExpect(status().isForbidden());

        verifyNoInteractions(resgateService);
    }
}
