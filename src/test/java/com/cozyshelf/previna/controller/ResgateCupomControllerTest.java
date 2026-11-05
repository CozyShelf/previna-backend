package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.CupomDisponivelResponse;
import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Passa pelo filtro JWT e pela SecurityConfig de verdade: o token é emitido pelo JwtService, como no login
@WebMvcTest(ResgateCupomController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + ResgateCupomControllerTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class ResgateCupomControllerTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CupomService cupomService;

    private final Medico medico = Campos.definir(
            new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false), "id", 2L);

    private Gestante gestante(Long id, boolean primeiroAcessoConcluido) {
        var gestante = Campos.definir(new Gestante("Maria", "12345678909", null, "12345678909", "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()), "id", id);
        return primeiroAcessoConcluido ? Campos.definir(gestante, "statusConta", StatusConta.ATIVA) : gestante;
    }

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }

    private String tokenGestante(Long id) {
        return token(gestante(id, true));
    }

    private static CupomResgatadoResponse resgatado(int saldo) {
        return new CupomResgatadoResponse(1L, "ABCD2345", "10% de desconto em Fralda RN", "Farmácia Vida",
                LocalDate.now().plusDays(10), StatusCupomResgatado.ATIVO, Instant.now(), 100, saldo);
    }

    // ---- Autenticação e perfil ----

    @Test
    void semTokenNaoListaNemResgata() throws Exception {
        mvc.perform(get("/cupons/disponiveis")).andExpect(status().isUnauthorized());
        mvc.perform(post("/cupons/1/resgatar")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cupomService);
    }

    @Test
    void tokenAdulteradoNaoAutentica() throws Exception {
        String valido = jwtService.gerarToken(gestante(7L, true));
        // Troca o último caractere da assinatura
        String adulterado = valido.substring(0, valido.length() - 1) + (valido.endsWith("A") ? "B" : "A");

        mvc.perform(post("/cupons/1/resgatar").header("Authorization", "Bearer " + adulterado))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cupomService);
    }

    @Test
    void tokenAssinadoComOutroSegredoNaoAutentica() throws Exception {
        String forjado = Jwts.builder()
                .subject("7").claim("perfil", "GESTANTE").claim("primeiroAcesso", false)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("outro-segredo-qualquer-com-32-bytes-ou-mais".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(post("/cupons/1/resgatar").header("Authorization", "Bearer " + forjado))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cupomService);
    }

    @Test
    void tokenExpiradoNaoAutentica() throws Exception {
        String expirado = Jwts.builder()
                .subject("7").claim("perfil", "GESTANTE").claim("primeiroAcesso", false)
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mvc.perform(post("/cupons/1/resgatar").header("Authorization", "Bearer " + expirado))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cupomService);
    }

    @Test
    void gestanteNoPrimeiroAcessoNaoResgata() throws Exception {
        // Ainda com a senha temporária: o token só serve para concluir o primeiro acesso
        String temporario = token(gestante(7L, false));

        mvc.perform(get("/cupons/disponiveis").header("Authorization", temporario)).andExpect(status().isForbidden());
        mvc.perform(post("/cupons/1/resgatar").header("Authorization", temporario)).andExpect(status().isForbidden());
        verifyNoInteractions(cupomService);
    }

    @Test
    void medicoNaoResgata() throws Exception {
        mvc.perform(post("/cupons/1/resgatar").header("Authorization", token(medico)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cupomService);
    }

    @Test
    void empresaNaoResgataNemOsProprioCupons() throws Exception {
        var empresa = Campos.definir(Campos.definir(
                new EmpresaParceira("Farmácia Vida", "12345678000190", null, null, null, "vida", "hash"), "id", 3L),
                "statusConta", StatusConta.ATIVA);

        mvc.perform(get("/cupons/disponiveis").header("Authorization", token(empresa)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cupons/1/resgatar").header("Authorization", token(empresa)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cupomService);
    }

    // ---- A gestante vem do token ----

    @Test
    void resgataComAGestanteDoTokenIgnorandoOutraNoCorpoOuNaQuery() throws Exception {
        when(cupomService.resgatar(5L, 7L)).thenReturn(resgatado(300));

        mvc.perform(post("/cupons/5/resgatar?idGestante=99&gestante=99")
                        .header("Authorization", tokenGestante(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idGestante\": 99, \"gestante\": 99, \"pontos\": 0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("ABCD2345"))
                .andExpect(jsonPath("$.saldo").value(300))
                .andExpect(jsonPath("$.pontosGastos").value(100));

        verify(cupomService).resgatar(5L, 7L);
        verify(cupomService, never()).resgatar(anyLong(), eq(99L));
    }

    @Test
    void listaOsDisponiveis() throws Exception {
        var empresa = new CupomDisponivelResponse.Empresa(3L, "Farmácia Vida", "Farmácia", "/empresas/3/logo?v=1");
        when(cupomService.listarDisponiveis()).thenReturn(List.of(new CupomDisponivelResponse(1L,
                "10% de desconto em Fralda RN", "Fralda RN", Categoria.FRALDAS, null, BigDecimal.TEN,
                LocalDate.now().plusDays(10), 100, empresa)));

        mvc.perform(get("/cupons/disponiveis").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].empresa.nome").value("Farmácia Vida"))
                .andExpect(jsonPath("$[0].pontosNecessarios").value(100))
                // Nulos ficam fora do JSON (default-property-inclusion: non_null no application.yml)
                .andExpect(jsonPath("$[0].empresa.cnpj").doesNotExist());
    }

    // ---- Erros do diagrama: status e mensagem ----

    @Test
    void cupomInexistenteResponde404ComAMensagem() throws Exception {
        when(cupomService.resgatar(999L, 7L)).thenThrow(new NaoEncontradoException("Cupom não encontrado"));

        mvc.perform(post("/cupons/999/resgatar").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cupom não encontrado"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Cupom indisponível", "Pontos insuficientes"})
    void regraVioladaResponde409ComAMensagem(String mensagem) throws Exception {
        when(cupomService.resgatar(5L, 7L)).thenThrow(new ConflitoException(mensagem));

        mvc.perform(post("/cupons/5/resgatar").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(mensagem));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "1;DROP TABLE cupom", "99999999999999999999"})
    void idInvalidoNoCaminhoNaoChegaAoServico(String id) throws Exception {
        mvc.perform(post("/cupons/{id}/resgatar", id).header("Authorization", tokenGestante(7L)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(cupomService);
    }

    @Test
    void listarNaoAceitaPost() throws Exception {
        mvc.perform(post("/cupons/disponiveis").header("Authorization", tokenGestante(7L)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(cupomService);
    }

    @Test
    void resgatarNaoAceitaGet() throws Exception {
        mvc.perform(get("/cupons/5/resgatar").header("Authorization", tokenGestante(7L)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(cupomService);
    }
}
