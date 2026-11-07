package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.service.AgendamentoService;
import com.cozyshelf.previna.application.service.AuthService;
import com.cozyshelf.previna.application.service.ConteudoService;
import com.cozyshelf.previna.application.service.CupomService;
import com.cozyshelf.previna.application.service.EmpresaService;
import com.cozyshelf.previna.application.service.GestanteService;
import com.cozyshelf.previna.application.service.MedicoService;
import com.cozyshelf.previna.application.service.PontuacaoService;
import com.cozyshelf.previna.application.service.ProdutoService;
import com.cozyshelf.previna.application.service.PromocaoService;
import com.cozyshelf.previna.application.service.TipoAtendimentoService;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Perfil;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.infrastructure.config.SecurityConfig;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.cozyshelf.previna.controller.MatrizAutorizacaoTest.Papel.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

// Matriz de autorização: cada rota de cada controller contra os seis tipos de quem chama.
// Sobe todos os controllers com a SecurityConfig e o JwtService de verdade; os serviços são simulados, então o
// que se prova aqui é só quem passa pela porta. Rota nova = uma linha em ROTAS (o teste de completude cobra).
@WebMvcTest
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "previna.security.jwt.secret=" + MatrizAutorizacaoTest.SEGREDO,
        "previna.security.jwt.expiracao-minutos=60",
        "previna.security.jwt.expiracao-gestante-dias=30"
})
class MatrizAutorizacaoTest {
    static final String SEGREDO = "segredo-de-teste-com-no-minimo-32-bytes!!";

    // Quem chama: ANONIMO sem token; PRIMEIRO_ACESSO_* é a conta ainda com a senha temporária, de cada tipo
    enum Papel { ANONIMO, ADMIN, MEDICO, GESTANTE, EMPRESA_PARCEIRA, PRIMEIRO_ACESSO_GESTANTE, PRIMEIRO_ACESSO_EMPRESA }

    private static final Set<Papel> PUBLICA = EnumSet.allOf(Papel.class);
    private static final Set<Papel> AUTENTICADA = EnumSet.complementOf(EnumSet.of(ANONIMO));

    private static final String MEDICO_VALIDO = """
            {"nome":"Ana Souza","cpf":"52998224725","email":"ana@clinica.com","crm":"CRM-SP 1",
             "login":"ana.souza","senha":"senha1234","verTodasGestantes":false}""";
    private static final String TIPO_VALIDO = """
            {"nome":"Ultrassom 4D","categoria":"EXAME","pontosBase":30,"ativo":true}""";
    private static final Map<String, String> CONTEUDO_VALIDO = Map.of("titulo", "Pré-natal em dia",
            "categoria", "DICA", "tipo", "TEXTO", "corpo", "Vá a todas as consultas.");

    // ---- A matriz: método, caminho (como no @XxxMapping), corpo e quem pode ----

    private static final List<Rota> ROTAS = List.of(
            // Públicas: verificação da API, login e imagens (a tag <img> não envia o token)
            Rota.get("/", PUBLICA),
            Rota.post("/auth/login", json("{\"login\":\"ana\",\"senha\":\"segredo123\"}"), PUBLICA),
            Rota.get("/empresas/{id}/logo", PUBLICA),
            Rota.get("/empresas/produtos/{id}/imagem", PUBLICA),
            Rota.get("/conteudos/{id}/imagem", PUBLICA),

            // Qualquer autenticado, inclusive no primeiro acesso (o portal precisa saber quem entrou)
            Rota.get("/auth/perfil", AUTENTICADA),

            // Administrador
            Rota.get("/medicos", EnumSet.of(ADMIN)),
            Rota.get("/medicos/{id}", EnumSet.of(ADMIN)),
            Rota.post("/medicos", json(MEDICO_VALIDO), EnumSet.of(ADMIN)),
            Rota.put("/medicos/{id}", json(MEDICO_VALIDO), EnumSet.of(ADMIN)),
            Rota.get("/empresas", EnumSet.of(ADMIN)),
            Rota.post("/empresas/pre-cadastro", json("""
                    {"razaoSocial":"Farmácia Vida","cnpj":"11222333000181","email":"contato@vida.com",
                     "telefone":"1133334444","responsavel":"Carla"}"""), EnumSet.of(ADMIN)),
            Rota.postMultipart("/conteudos", CONTEUDO_VALIDO, EnumSet.of(ADMIN)),
            Rota.putMultipart("/conteudos/{id}", CONTEUDO_VALIDO, EnumSet.of(ADMIN)),
            Rota.put("/conteudos/{id}/publicado", json("{\"publicado\":true}"), EnumSet.of(ADMIN)),
            Rota.delete("/conteudos/{id}", EnumSet.of(ADMIN)),

            // Administrador e gestante: o admin vê também os rascunhos, a gestante só os publicados
            Rota.get("/conteudos", EnumSet.of(ADMIN, GESTANTE)),
            Rota.get("/conteudos/{id}", EnumSet.of(ADMIN, GESTANTE)),

            // Médico
            Rota.get("/gestantes", EnumSet.of(MEDICO)),
            Rota.post("/gestantes/pre-cadastro", json("""
                    {"nome":"Maria","cpf":"52998224725","dataNascimento":"1995-01-01",
                     "telefone":"11999999999","dum":"%s"}""".formatted(LocalDate.now().minusDays(30))),
                    EnumSet.of(MEDICO)),
            Rota.get("/gestantes/{id}", EnumSet.of(MEDICO)),
            Rota.get("/gestantes/{id}/agendamentos", EnumSet.of(MEDICO)),
            Rota.post("/agendamentos", json("""
                    {"gestante":1,"itens":[{"tipoAtendimento":1,"dataPrevista":"%s"}]}"""
                    .formatted(LocalDate.now().plusDays(7))), EnumSet.of(MEDICO)),
            Rota.get("/agendamentos/hoje", EnumSet.of(MEDICO)),
            Rota.put("/agendamentos/{id}/confirmar", EnumSet.of(MEDICO)),
            Rota.put("/agendamentos/{id}/nao-compareceu", EnumSet.of(MEDICO)),
            Rota.get("/tipos-atendimento", EnumSet.of(MEDICO)),
            Rota.get("/tipos-atendimento/meus", EnumSet.of(MEDICO)),
            Rota.post("/tipos-atendimento", json(TIPO_VALIDO), EnumSet.of(MEDICO)),
            Rota.get("/tipos-atendimento/{id}", EnumSet.of(MEDICO)),
            Rota.put("/tipos-atendimento/{id}", json(TIPO_VALIDO), EnumSet.of(MEDICO)),
            Rota.delete("/tipos-atendimento/{id}", EnumSet.of(MEDICO)),

            // Gestante
            Rota.get("/gestantes/agendamentos", EnumSet.of(GESTANTE)),
            Rota.get("/gestantes/perfil", EnumSet.of(GESTANTE)),
            Rota.putMultipart("/gestantes/perfil", Map.of("telefone", "11912345678"), EnumSet.of(GESTANTE)),
            Rota.get("/gestantes/foto", EnumSet.of(GESTANTE)),
            Rota.put("/gestantes/tutorial-concluido", EnumSet.of(GESTANTE)),
            Rota.get("/gestantes/pontuacao", EnumSet.of(GESTANTE)),
            Rota.get("/gestantes/extrato", EnumSet.of(GESTANTE)),
            Rota.get("/cupons/disponiveis", EnumSet.of(GESTANTE)),
            Rota.post("/cupons/{id}/resgatar", EnumSet.of(GESTANTE)),

            // Empresa parceira
            Rota.get("/empresas/perfil", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.putMultipart("/empresas/perfil", Map.of("email", "contato@vida.com", "telefone", "1133334444",
                    "responsavel", "Carla", "endereco", "Rua A, 1", "segmento", "Farmácia"),
                    EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.get("/empresas/produtos", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.postMultipart("/empresas/produtos", Map.of("nome", "Fralda RN"), EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.get("/empresas/produtos/{id}", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.putMultipart("/empresas/produtos/{id}", Map.of("nome", "Fralda RN"), EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.get("/empresas/promocoes", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.post("/empresas/promocoes", json("""
                    {"titulo":"Semana do bebê","desconto":10,"dataInicio":"%s","dataFim":"%s","categoria":"FRALDAS"}"""
                    .formatted(LocalDate.now(), LocalDate.now().plusDays(7))), EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.get("/empresas/promocoes/{id}", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.put("/empresas/promocoes/{id}", json("""
                    {"titulo":"Semana do bebê","desconto":10,"dataInicio":"%s","dataFim":"%s","categoria":"FRALDAS"}"""
                    .formatted(LocalDate.now(), LocalDate.now().plusDays(7))), EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.get("/empresas/cupons", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.post("/empresas/cupons", json("""
                    {"produto":1,"desconto":10,"dataValidade":"%s","pontosNecessarios":100}"""
                    .formatted(LocalDate.now().plusDays(30))), EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.get("/empresas/cupons/{id}", EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.put("/empresas/cupons/{id}", json("""
                    {"produto":1,"desconto":10,"dataValidade":"%s","pontosNecessarios":100}"""
                    .formatted(LocalDate.now().plusDays(30))), EnumSet.of(EMPRESA_PARCEIRA)),
            Rota.put("/empresas/cupons/{id}/ativo", json("{\"ativo\":false}"), EnumSet.of(EMPRESA_PARCEIRA)),

            // Primeiro acesso: só com o token temporário do mesmo tipo de conta (a gestante não conclui o da empresa)
            Rota.putMultipart("/gestantes/primeiro-acesso", Map.of("novoLogin", "maria.silva",
                    "novaSenha", "novaSenha1", "consentimentoLgpd", "true"), EnumSet.of(PRIMEIRO_ACESSO_GESTANTE)),
            Rota.putMultipart("/empresas/primeiro-acesso", Map.of("novoLogin", "farmacia.vida",
                    "novaSenha", "novaSenha1", "endereco", "Rua A, 1", "segmento", "Farmácia"),
                    EnumSet.of(PRIMEIRO_ACESSO_EMPRESA))
    );

    // ---- Infraestrutura ----

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    @MockitoBean
    private AgendamentoService agendamentoService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private ConteudoService conteudoService;
    @MockitoBean
    private CupomService cupomService;
    @MockitoBean
    private EmpresaService empresaService;
    @MockitoBean
    private GestanteService gestanteService;
    @MockitoBean
    private MedicoService medicoService;
    @MockitoBean
    private PontuacaoService pontuacaoService;
    @MockitoBean
    private ProdutoService produtoService;
    @MockitoBean
    private PromocaoService promocaoService;
    @MockitoBean
    private TipoAtendimentoService tipoAtendimentoService;

    private Object[] servicos() {
        return new Object[]{agendamentoService, authService, conteudoService, cupomService, empresaService, gestanteService,
                medicoService, pontuacaoService, produtoService, promocaoService, tipoAtendimentoService};
    }

    @AfterEach
    void limpar() {
        reset(servicos());
    }

    static Stream<Arguments> combinacoes() {
        return ROTAS.stream().flatMap(rota -> Arrays.stream(Papel.values()).map(papel -> Arguments.of(rota, papel)));
    }

    @ParameterizedTest(name = "{0} como {1}")
    @MethodSource("combinacoes")
    void soQuemPodeChegaAoServico(Rota rota, Papel papel) throws Exception {
        int status = mvc.perform(rota.requisicao(token(papel))).andReturn().getResponse().getStatus();

        if (rota.permitidos().contains(papel)) {
            assertNotEquals(401, status, "barrado como não autenticado");
            assertNotEquals(403, status, "barrado por falta de permissão");
            // Corpo válido: a requisição tem de chegar ao serviço, senão o "não é 401/403" não prova nada.
            // A verificação da API ("/") responde sozinha, sem serviço.
            if (!rota.toString().equals("GET /")) {
                assertTrue(Arrays.stream(servicos()).anyMatch(s -> !mockingDetails(s).getInvocations().isEmpty()),
                        "nenhum serviço foi chamado (status " + status + ")");
            }
        } else {
            // Sem token: 401; com token de outro papel: 403. Nos dois casos, nada é executado
            assertEquals(papel == ANONIMO ? 401 : 403, status);
            verifyNoInteractions(servicos());
        }
    }

    // Papel errado com corpo inválido: 403, e não 400. A checagem de papel vem antes da leitura e da validação do
    // corpo (AutorizacaoAntesDaValidacao); antes, o 400 revelava as regras de validação a quem não tinha permissão.
    static Stream<Arguments> corposInvalidosDePapelErrado() {
        return ROTAS.stream()
                .filter(rota -> rota.corpo() != Corpo.VAZIO)
                .flatMap(rota -> Arrays.stream(Papel.values())
                        .filter(papel -> papel != ANONIMO && !rota.permitidos().contains(papel))
                        .map(papel -> Arguments.of(rota.comCorpoInvalido(), papel)));
    }

    @ParameterizedTest(name = "{0} como {1} com corpo inválido")
    @MethodSource("corposInvalidosDePapelErrado")
    void papelErradoComCorpoInvalidoRecebe403(Rota rota, Papel papel) throws Exception {
        mvc.perform(rota.requisicao(token(papel))).andExpect(status().isForbidden());
        verifyNoInteractions(servicos());
    }

    @Test
    void papelCertoComCorpoInvalidoContinuaRecebendo400() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/medicos").header("Authorization", token(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicos());
    }

    // Toda rota mapeada nos controllers tem de estar na matriz, e a matriz não pode ter rota que não existe
    @Test
    void matrizCobreTodasAsRotasDosControllers() {
        Set<String> mapeadas = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeamentos.getHandlerMethods().entrySet()) {
            if (!entrada.getValue().getBeanType().getPackageName().equals(getClass().getPackageName())) {
                continue;
            }
            RequestMappingInfo info = entrada.getKey();
            for (var metodo : info.getMethodsCondition().getMethods()) {
                for (String caminho : info.getPatternValues()) {
                    mapeadas.add(metodo.name() + " " + caminho);
                }
            }
        }
        Set<String> naMatriz = ROTAS.stream().map(Rota::toString).collect(Collectors.toCollection(TreeSet::new));

        assertEquals(mapeadas, naMatriz);
    }

    private String token(Papel papel) {
        Usuario usuario = switch (papel) {
            case ANONIMO -> null;
            case ADMIN -> ativo(administrador());
            case MEDICO -> new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false);
            case GESTANTE -> ativo(gestante());
            case EMPRESA_PARCEIRA -> ativo(new EmpresaParceira("Farmácia Vida", "11222333000181", null, null, null,
                    "vida", "hash"));
            // Contas recém pré-cadastradas: o token sai com o papel PRIMEIRO_ACESSO_<perfil>
            case PRIMEIRO_ACESSO_GESTANTE -> gestante();
            case PRIMEIRO_ACESSO_EMPRESA -> new EmpresaParceira("Farmácia Vida", "11222333000181", null, null, null,
                    "vida", "hash");
        };
        return usuario == null ? null : "Bearer " + jwtService.gerarToken(Campos.definir(usuario, "id", 7L));
    }

    private static Gestante gestante() {
        return new Gestante("Maria", "12345678909", null, "12345678909", "hash", null,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());
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

    private static <T extends Usuario> T ativo(T usuario) {
        return Campos.definir(usuario, "statusConta", StatusConta.ATIVA);
    }

    private static Corpo json(String conteudo) {
        return new Corpo(conteudo, null);
    }

    // JSON ou campos de multipart; os dois nulos = sem corpo
    record Corpo(String json, Map<String, String> multipart) {
        static final Corpo VAZIO = new Corpo(null, null);
    }

    record Rota(HttpMethod metodo, String caminho, Corpo corpo, Set<Papel> permitidos) {
        static Rota get(String caminho, Set<Papel> permitidos) {
            return new Rota(HttpMethod.GET, caminho, Corpo.VAZIO, permitidos);
        }

        static Rota delete(String caminho, Set<Papel> permitidos) {
            return new Rota(HttpMethod.DELETE, caminho, Corpo.VAZIO, permitidos);
        }

        static Rota post(String caminho, Set<Papel> permitidos) {
            return new Rota(HttpMethod.POST, caminho, Corpo.VAZIO, permitidos);
        }

        static Rota post(String caminho, Corpo corpo, Set<Papel> permitidos) {
            return new Rota(HttpMethod.POST, caminho, corpo, permitidos);
        }

        static Rota put(String caminho, Set<Papel> permitidos) {
            return new Rota(HttpMethod.PUT, caminho, Corpo.VAZIO, permitidos);
        }

        static Rota put(String caminho, Corpo corpo, Set<Papel> permitidos) {
            return new Rota(HttpMethod.PUT, caminho, corpo, permitidos);
        }

        static Rota postMultipart(String caminho, Map<String, String> campos, Set<Papel> permitidos) {
            return new Rota(HttpMethod.POST, caminho, new Corpo(null, campos), permitidos);
        }

        static Rota putMultipart(String caminho, Map<String, String> campos, Set<Papel> permitidos) {
            return new Rota(HttpMethod.PUT, caminho, new Corpo(null, campos), permitidos);
        }

        // Mesmo método e caminho, com um corpo que reprova na validação (JSON vazio ou multipart sem campos)
        Rota comCorpoInvalido() {
            Corpo invalido = corpo.multipart() != null ? new Corpo(null, Map.of()) : new Corpo("{}", null);
            return new Rota(metodo, caminho, invalido, permitidos);
        }

        MockHttpServletRequestBuilder requisicao(String token) {
            String uri = caminho.replace("{id}", "1");
            MockHttpServletRequestBuilder req;
            if (corpo.multipart() != null) {
                MockMultipartHttpServletRequestBuilder multipart = multipart(metodo, uri);
                corpo.multipart().forEach(multipart::param);
                req = multipart;
            } else {
                req = request(metodo, uri);
                if (corpo.json() != null) {
                    req.contentType(MediaType.APPLICATION_JSON).content(corpo.json());
                }
            }
            return token == null ? req : req.header("Authorization", token);
        }

        @Override
        public String toString() {
            return metodo.name() + " " + caminho;
        }
    }
}
