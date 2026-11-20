package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.dto.response.ListaCurtaResponse;
import com.cozyshelf.previna.application.dto.response.PainelAdminResponse;
import com.cozyshelf.previna.application.dto.response.PainelEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.PainelMedicoResponse;
import com.cozyshelf.previna.application.service.PainelAdminService;
import com.cozyshelf.previna.application.service.PainelEmpresaService;
import com.cozyshelf.previna.application.service.PainelMedicoService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.AcaoAuditoria;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O contrato do painel da tela Início com o portal: o usuário vem do token e o JSON sai no formato combinado (datas
// "aaaa-mm-dd", instantes ISO em UTC, nulos presentes onde o contrato prevê). Quem pode chamar cada rota está no
// MatrizAutorizacaoTest; os números, no PainelIntegracaoTest.
@WebMvcTest(PainelController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + PainelControllerTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class PainelControllerTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private PainelMedicoService medicoService;
    @MockitoBean
    private PainelEmpresaService empresaService;
    @MockitoBean
    private PainelAdminService adminService;

    private String token(Usuario usuario, Long id) {
        return "Bearer " + jwtService.gerarToken(Campos.definir(Campos.definir(usuario, "id", id),
                "statusConta", StatusConta.ATIVA));
    }

    @Test
    void painelDoMedicoComOMedicoDoToken() throws Exception {
        var atrasado = new PainelMedicoResponse.AgendamentoPendente(7L, 4L, "Maria", "Ultrassom",
                CategoriaAtendimento.EXAME, LocalDate.of(2026, 11, 10));
        var proximo = new PainelMedicoResponse.AgendamentoPendente(8L, 4L, "Maria", "Consulta de rotina",
                CategoriaAtendimento.CONSULTA, LocalDate.of(2026, 11, 22));
        when(medicoService.painel(5L)).thenReturn(new PainelMedicoResponse(12,
                new ListaCurtaResponse<>(3, List.of(atrasado)), new ListaCurtaResponse<>(5, List.of(proximo)),
                new ListaCurtaResponse<>(2, List.of(new PainelMedicoResponse.GestanteSemPendente(9L, "Ana")))));

        mvc.perform(get("/painel/medico").header("Authorization",
                        token(new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false), 5L)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"gestantesAcompanhadas":12,
                         "atrasados":{"total":3,"itens":[{"agendamentoId":7,"gestanteId":4,"gestanteNome":"Maria",
                           "tipoNome":"Ultrassom","categoria":"EXAME","dataPrevista":"2026-11-10"}]},
                         "proximos7Dias":{"total":5,"itens":[{"agendamentoId":8,"gestanteId":4,"gestanteNome":"Maria",
                           "tipoNome":"Consulta de rotina","categoria":"CONSULTA","dataPrevista":"2026-11-22"}]},
                         "semAgendamentoPendente":{"total":2,"itens":[{"gestanteId":9,"gestanteNome":"Ana"}]}}""",
                        true));

        verify(medicoService).painel(5L);
    }

    @Test
    void painelDaEmpresaComAEmpresaDoToken() throws Exception {
        when(empresaService.painel(3L)).thenReturn(new PainelEmpresaResponse(4, 1, 6, 10,
                new ListaCurtaResponse<>(1, List.of(new PainelEmpresaResponse.CupomVencendo(3L, "Fralda RN",
                        LocalDate.of(2026, 11, 25)))),
                new ListaCurtaResponse<>(2, List.of(new PainelEmpresaResponse.ProdutoSemOferta(8L, "Body"))),
                List.of(new PainelEmpresaResponse.Validacao(12L, "AB12CD34", TipoResgate.CUPOM, null, "Fralda RN",
                                Instant.parse("2026-11-19T14:05:00Z")),
                        new PainelEmpresaResponse.Validacao(11L, "EF56GH78", TipoResgate.PROMOCAO, "Semana do bebê",
                                null, Instant.parse("2026-11-18T10:00:00Z")))));

        mvc.perform(get("/painel/empresa").header("Authorization",
                        token(new EmpresaParceira("Loja X", "12345678000190", null, null, null, "loja", "hash"), 3L)))
                .andExpect(status().isOk())
                // titulo e produtoNome nulos vão no JSON
                .andExpect(content().json("""
                        {"cuponsAtivos":4,"promocoesVigentes":1,"resgatesAguardandoUso":6,"validacoes30Dias":10,
                         "cuponsVencendo7Dias":{"total":1,"itens":[{"cupomId":3,"produtoNome":"Fralda RN",
                           "dataValidade":"2026-11-25"}]},
                         "produtosSemOferta":{"total":2,"itens":[{"produtoId":8,"nome":"Body"}]},
                         "ultimasValidacoes":[
                           {"resgateId":12,"codigo":"AB12CD34","tipo":"CUPOM","titulo":null,"produtoNome":"Fralda RN",
                            "dataUso":"2026-11-19T14:05:00Z"},
                           {"resgateId":11,"codigo":"EF56GH78","tipo":"PROMOCAO","titulo":"Semana do bebê",
                            "produtoNome":null,"dataUso":"2026-11-18T10:00:00Z"}]}""", true));

        verify(empresaService).painel(3L);
    }

    @Test
    void painelDoAdmin() throws Exception {
        when(adminService.painel()).thenReturn(new PainelAdminResponse(5, new PainelAdminResponse.Contas(9, 2),
                new PainelAdminResponse.Contas(30, 4), new PainelAdminResponse.Conteudos(8, 2),
                new ListaCurtaResponse<>(2, List.of(new PainelAdminResponse.EmpresaPendente(3L, "Loja X",
                        Instant.parse("2026-11-15T10:00:00Z")))),
                List.of(new PainelAdminResponse.Atividade(Instant.parse("2026-11-20T01:00:00Z"),
                                AcaoAuditoria.AGENDAMENTO_REALIZADO, "Médico Teste", Perfil.MEDICO, 50),
                        new PainelAdminResponse.Atividade(Instant.parse("2026-11-19T23:00:00Z"),
                                AcaoAuditoria.PROMOCAO_RESGATADA, null, Perfil.GESTANTE, null))));

        mvc.perform(get("/painel/admin").header("Authorization", token(administrador(), 1L)))
                .andExpect(status().isOk())
                // A gestante não tem nome na atividade; pontos nulo vai no JSON
                .andExpect(content().json("""
                        {"medicos":5,"empresas":{"total":9,"primeiroAcessoPendente":2},
                         "gestantes":{"total":30,"primeiroAcessoPendente":4},
                         "conteudos":{"publicados":8,"rascunhos":2},
                         "empresasPendentes":{"total":2,"itens":[{"empresaId":3,"razaoSocial":"Loja X",
                           "preCadastradaEm":"2026-11-15T10:00:00Z"}]},
                         "atividadeRecente":[
                           {"dataHora":"2026-11-20T01:00:00Z","acao":"AGENDAMENTO_REALIZADO",
                            "usuarioNome":"Médico Teste","perfil":"MEDICO","pontos":50},
                           {"dataHora":"2026-11-19T23:00:00Z","acao":"PROMOCAO_RESGATADA",
                            "usuarioNome":null,"perfil":"GESTANTE","pontos":null}]}""", true));
    }

    // O administrador é um Usuario sem subclasse; o construtor é protegido porque só o banco o cria
    private static Usuario administrador() {
        try {
            Constructor<Usuario> construtor = Usuario.class.getDeclaredConstructor();
            construtor.setAccessible(true);
            return Campos.definir(construtor.newInstance(), "perfil", Perfil.ADMIN);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
