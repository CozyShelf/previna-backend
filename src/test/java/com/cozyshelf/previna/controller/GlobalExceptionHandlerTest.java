package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.request.LoginRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.application.service.AuthService;
import com.cozyshelf.previna.application.service.MedicoService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exceções dos serviços viram status HTTP com a mensagem no corpo, pelo GlobalExceptionHandler
@WebMvcTest({AuthController.class, MedicoController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=segredo-de-teste-com-no-minimo-32-bytes!!",
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class GlobalExceptionHandlerTest {
    private static final String MEDICO = """
            {"nome":"Ana Souza","cpf":"52998224725","email":"ana@clinica.com","crm":"CRM-SP 1",
             "login":"ana.souza","senha":"senha1234","verTodasGestantes":false}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private MedicoService medicoService;

    private String tokenAdmin() throws Exception {
        Constructor<Usuario> construtor = Usuario.class.getDeclaredConstructor();
        construtor.setAccessible(true);
        Usuario admin = Campos.definir(Campos.definir(Campos.definir(construtor.newInstance(),
                "id", 1L), "perfil", Perfil.ADMIN), "statusConta", StatusConta.ATIVA);
        return "Bearer " + jwtService.gerarToken(admin);
    }

    @Test
    void credenciaisInvalidasResponde401ComAMensagem() throws Exception {
        when(authService.autenticar(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Login ou senha inválidos"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"ana\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Login ou senha inválidos"));
    }

    @Test
    void dadosInvalidosResponde400ComAMensagem() throws Exception {
        when(medicoService.cadastrar(any())).thenThrow(new DadosInvalidosException("Informe a senha do médico"));

        mvc.perform(post("/medicos").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(MEDICO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe a senha do médico"));
    }

    @Test
    void conflitoResponde409ComAMensagem() throws Exception {
        when(medicoService.cadastrar(any())).thenThrow(new ConflitoException("CRM já cadastrado"));

        mvc.perform(post("/medicos").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(MEDICO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("CRM já cadastrado"));
    }

    // Corrida entre duas requisições que passam pela checagem e esbarram no UNIQUE do banco: 409 genérico,
    // sem vazar o nome da constraint nem o SQL
    @Test
    void violacaoDeUnicidadeNoBancoResponde409Generico() throws Exception {
        when(medicoService.cadastrar(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"usuario_cpf_key\""));

        mvc.perform(post("/medicos").header("Authorization", tokenAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(MEDICO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dados já cadastrados"));
    }

    @Test
    void naoEncontradoResponde404ComAMensagem() throws Exception {
        when(medicoService.buscar(anyLong())).thenThrow(new NaoEncontradoException("Médico não encontrado"));

        mvc.perform(get("/medicos/9").header("Authorization", tokenAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Médico não encontrado"));
    }
}
