package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.CupomResgatadoResponse;
import com.cozyshelf.previna.application.exception.CodigoErro;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.application.service.PromocaoService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Resgate de promoção pela gestante: o mesmo contrato do resgate de cupom (Idempotency-Key, 201/200), sem pontos
@WebMvcTest(ResgatePromocaoController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + ResgatePromocaoControllerTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class ResgatePromocaoControllerTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";
    private static final String CHAVE = "3f1c9a52-7d0e-4b8a-9c61-2e5f4a7b8d90";
    private static final LocalDate FIM = LocalDate.now().plusDays(5);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private PromocaoService promocaoService;

    private final Medico medico = Campos.definir(
            new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false), "id", 2L);

    private String token(Usuario usuario) {
        return "Bearer " + jwtService.gerarToken(usuario);
    }

    private String tokenGestante(Long id) {
        var gestante = Campos.definir(new Gestante("Maria", "12345678909", null, "12345678909", "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()), "id", id);
        return token(Campos.definir(gestante, "statusConta", StatusConta.ATIVA));
    }

    private static CupomService.Resgate resgate(boolean repetido) {
        return new CupomService.Resgate(new CupomResgatadoResponse(1L, TipoResgate.PROMOCAO, "ABCD2345",
                "15% de desconto em Fraldas", "Farmácia Vida", FIM, StatusCupomResgatado.ATIVO, Instant.now(), 0,
                120), repetido);
    }

    @Test
    void resgataComAGestanteDoTokenE201() throws Exception {
        when(promocaoService.resgatar(5L, 7L, null)).thenReturn(resgate(false));

        mvc.perform(post("/promocoes/5/resgatar?idGestante=99").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("PROMOCAO"))
                .andExpect(jsonPath("$.codigo").value("ABCD2345"))
                .andExpect(jsonPath("$.descricao").value("15% de desconto em Fraldas"))
                .andExpect(jsonPath("$.empresa").value("Farmácia Vida"))
                .andExpect(jsonPath("$.dataValidade").value(FIM.toString()))
                .andExpect(jsonPath("$.pontosGastos").value(0))
                .andExpect(jsonPath("$.saldo").value(120));

        verify(promocaoService, never()).resgatar(anyLong(), eq(99L), any());
    }

    @Test
    void chaveNovaResponde201EChaveRepetida200() throws Exception {
        when(promocaoService.resgatar(5L, 7L, UUID.fromString(CHAVE))).thenReturn(resgate(false), resgate(true));

        mvc.perform(post("/promocoes/5/resgatar").header("Authorization", tokenGestante(7L))
                        .header("Idempotency-Key", CHAVE))
                .andExpect(status().isCreated());
        mvc.perform(post("/promocoes/5/resgatar").header("Authorization", tokenGestante(7L))
                        .header("Idempotency-Key", CHAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("ABCD2345"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1-2-3-4-5", "3f1c9a527d0e4b8a9c612e5f4a7b8d90"})
    void chaveQueNaoEUuidResponde400SemChegarAoServico(String chave) throws Exception {
        mvc.perform(post("/promocoes/5/resgatar").header("Authorization", tokenGestante(7L))
                        .header("Idempotency-Key", chave))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key deve ser um UUID"));
        verifyNoInteractions(promocaoService);
    }

    @Test
    void promocaoInexistenteResponde404() throws Exception {
        when(promocaoService.resgatar(999L, 7L, null)).thenThrow(new NaoEncontradoException("Promoção não encontrada"));

        mvc.perform(post("/promocoes/999/resgatar").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Promoção não encontrada"));
    }

    @ParameterizedTest
    @CsvSource({
            "Promoção indisponível, PROMOCAO_INDISPONIVEL",
            "Promoção já resgatada, PROMOCAO_JA_RESGATADA"
    })
    void regraVioladaResponde409ComAMensagemEOCodigo(String mensagem, CodigoErro codigo) throws Exception {
        when(promocaoService.resgatar(5L, 7L, null)).thenThrow(new ConflitoException(mensagem, codigo));

        mvc.perform(post("/promocoes/5/resgatar").header("Authorization", tokenGestante(7L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(mensagem))
                .andExpect(jsonPath("$.codigo").value(codigo.name()));
    }

    @Test
    void semTokenOuComTokenDeEmpresaNaoResgata() throws Exception {
        var empresa = Campos.definir(Campos.definir(new EmpresaParceira("Farmácia Vida", "12345678000190", null,
                null, null, "vida", "hash"), "id", 3L), "statusConta", StatusConta.ATIVA);

        mvc.perform(post("/promocoes/5/resgatar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/promocoes/5/resgatar").header("Authorization", token(empresa)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(promocaoService);
    }
}
