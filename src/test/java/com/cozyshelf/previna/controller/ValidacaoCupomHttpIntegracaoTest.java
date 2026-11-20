package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.service.GeradorCodigoResgate;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

// Validação do código na loja (POST /empresas/cupons/validar) de ponta a ponta: servidor numa porta aleatória, filtro
// JWT, controller, serviço e PostgreSQL. Cada resposta do contrato com o portal, o status gravado no banco, o que o app
// da gestante passa a ver e duas validações simultâneas do mesmo código com a disputa forçada (com uma linha só no log
// de auditoria).
// Roda só com PREVINA_TEST_DB_URL (ver ResgateCupomIntegracaoTest e o README).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class ValidacaoCupomHttpIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes
    private static final AtomicLong SEQUENCIA =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L + 400_000_000L);
    private static final int ESPERA_SEGUNDOS = 15;
    private static final LocalDate HOJE = LocalDate.now();

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper json;
    @MockitoSpyBean
    private CupomResgatadoRepository resgatadoRepository;
    @Autowired
    private GeradorCodigoResgate geradorCodigo;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private GestanteRepository gestanteRepository;
    @Autowired
    private EmpresaParceiraRepository empresaRepository;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private CupomRepository cupomRepository;
    @Autowired
    private PromocaoRepository promocaoRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Gestante gestante;
    private EmpresaParceira empresa;
    private ExecutorService executor;

    @BeforeEach
    void preparar() {
        reset(resgatadoRepository);
        executor = Executors.newFixedThreadPool(2);
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medicoRepository.save(new Medico("Dra. Ana", cpf(n), null, "CRM-V" + n, "medval" + n, "hash",
                false));
        n = SEQUENCIA.incrementAndGet();
        Gestante nova = gestanteRepository.save(new Gestante("Maria Souza", cpf(n), "maria" + n + "@exemplo.com",
                "gesval" + n, "hash", medico, LocalDate.of(1995, 1, 1), "11999999999", HOJE));
        ativar(nova);
        // Relida: o token sai do status da conta, e o papel do primeiro acesso não chega às rotas
        gestante = gestanteRepository.findById(nova.getId()).orElseThrow();
        empresa = empresaAtiva();
    }

    @AfterEach
    void encerrar() {
        executor.shutdownNow();
    }

    // ---- Dados ----

    private static String cpf(long n) {
        return "%011d".formatted(n);
    }

    private void ativar(Usuario usuario) {
        jdbc.update("UPDATE usuario SET status_conta = 'ATIVA' WHERE id = ?", usuario.getId());
    }

    private EmpresaParceira empresaAtiva() {
        long n = SEQUENCIA.incrementAndGet();
        EmpresaParceira nova = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n), null,
                null, null, "empval" + n, "hash"));
        ativar(nova);
        return empresaRepository.findById(nova.getId()).orElseThrow();
    }

    // Resgate de cupom (10% em Fralda RN, 50 pontos) da gestante na empresa, válido até a data indicada
    private CupomResgatado resgateDeCupom(EmpresaParceira dona, LocalDate validade) {
        Produto fralda = produtoRepository.save(new Produto(dona.getId(), "Fralda RN", null, Categoria.FRALDAS,
                null));
        Cupom cupom = cupomRepository.save(new Cupom(dona.getId(), fralda, new BigDecimal("10"), validade, 50));
        return resgatadoRepository.save(new CupomResgatado(cupom, dona, gestante, geradorCodigo.gerarCodigoUnico(),
                null));
    }

    private CupomResgatado resgateDeCupom(LocalDate validade) {
        return resgateDeCupom(empresa, validade);
    }

    // Resgate da promoção "Semana do bebê" (15% em roupas) da gestante, válida até o fim indicado
    private CupomResgatado resgateDePromocao(LocalDate fim) {
        Promocao promocao = new Promocao(empresa.getId(), "Semana do bebê", null, new BigDecimal("15"),
                fim.minusDays(10), fim, Categoria.ROUPAS);
        // Desativada: a exclusão da V23 não deixa duas ativas da mesma categoria no mesmo período; o resgate é cópia
        promocao.alterarAtivo(false);
        promocao = promocaoRepository.save(promocao);
        return resgatadoRepository.save(new CupomResgatado(promocao, empresa, gestante,
                geradorCodigo.gerarCodigoUnico(), null));
    }

    private String status(CupomResgatado resgate) {
        return jdbc.queryForObject("SELECT status FROM cupom_resgatado WHERE id = ?", String.class, resgate.getId());
    }

    private int comDataUso(CupomResgatado resgate) {
        return jdbc.queryForObject("SELECT count(*) FROM cupom_resgatado WHERE id = ? AND data_uso IS NOT NULL",
                Integer.class, resgate.getId());
    }

    // ---- HTTP ----

    private HttpHeaders autenticado(Usuario usuario) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(jwtService.gerarToken(usuario));
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }

    private ResponseEntity<String> validar(Usuario quem, Object corpo) {
        return http.exchange("/empresas/cupons/validar", HttpMethod.POST, new HttpEntity<>(corpo, autenticado(quem)),
                String.class);
    }

    private ResponseEntity<String> validar(String codigo) {
        return validar(empresa, Map.of("codigo", codigo));
    }

    private ResponseEntity<String> validar(CupomResgatado resgate) {
        return validar(resgate.getCodigo());
    }

    private JsonNode corpo(ResponseEntity<String> resposta) throws Exception {
        return json.readTree(resposta.getBody());
    }

    private static void assertStatus(int esperado, ResponseEntity<String> resposta) {
        assertEquals(esperado, resposta.getStatusCode().value(), resposta.getBody());
    }

    // ---- 200 ----

    @Test
    void cupomValidoPassaAUtilizadoERespondeComACopiaEONomeDaGestante() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(200, resposta);
        JsonNode validado = corpo(resposta);
        assertEquals(resgate.getId(), validado.get("id").asLong());
        assertEquals("CUPOM", validado.get("tipo").asText());
        assertEquals(resgate.getCodigo(), validado.get("codigo").asText());
        assertTrue(validado.get("titulo").isNull());
        assertEquals("Fralda RN", validado.get("produtoNome").asText());
        assertEquals("FRALDAS", validado.get("categoria").asText());
        assertEquals(0, new BigDecimal("10").compareTo(validado.get("desconto").decimalValue()));
        assertEquals(HOJE.plusDays(5).toString(), validado.get("validade").asText());
        assertFalse(validado.get("dataResgate").isNull());
        assertFalse(validado.get("dataUso").isNull());
        assertEquals("Maria Souza", validado.get("gestanteNome").asText());
        // Da gestante, só o nome
        assertEquals(11, validado.size());
        assertFalse(resposta.getBody().contains(gestante.getEmail()));
        assertFalse(resposta.getBody().contains(gestante.getCpf()));
        assertEquals("UTILIZADO", status(resgate));
        assertEquals(1, comDataUso(resgate));
    }

    @Test
    void promocaoValidaPassaAUtilizadoComOTituloESemProduto() throws Exception {
        CupomResgatado resgate = resgateDePromocao(HOJE.plusDays(3));

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(200, resposta);
        JsonNode validado = corpo(resposta);
        assertEquals("PROMOCAO", validado.get("tipo").asText());
        assertEquals("Semana do bebê", validado.get("titulo").asText());
        assertTrue(validado.get("produtoNome").isNull());
        assertEquals("ROUPAS", validado.get("categoria").asText());
        assertEquals(0, new BigDecimal("15").compareTo(validado.get("desconto").decimalValue()));
        assertEquals(HOJE.plusDays(3).toString(), validado.get("validade").asText());
        assertEquals("Maria Souza", validado.get("gestanteNome").asText());
        assertEquals("UTILIZADO", status(resgate));
    }

    // A validade vale até o fim do dia, como na expiração da leitura
    @Test
    void noUltimoDiaDaValidadeAindaValida() {
        CupomResgatado resgate = resgateDeCupom(HOJE);

        assertStatus(200, validar(resgate));
        assertEquals("UTILIZADO", status(resgate));
    }

    // O portal pode mandar o código digitado como veio: em minúsculas e com espaços nas pontas
    @Test
    void codigoEmMinusculasEComEspacosValida() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));

        ResponseEntity<String> resposta = validar("  " + resgate.getCodigo().toLowerCase() + " \t");

        assertStatus(200, resposta);
        assertEquals(resgate.getCodigo(), corpo(resposta).get("codigo").asText());
        assertEquals("UTILIZADO", status(resgate));
    }

    // D2: vale a cópia gravada no resgate. Desativar ou editar o cupom (ou a promoção) depois não invalida o código,
    // e a resposta traz o desconto e a validade do resgate, não os de agora
    @Test
    void cupomDesativadoEEditadoDepoisDoResgateAindaValidaComACopia() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));
        jdbc.update("UPDATE cupom SET ativo = FALSE, desconto = 50, data_validade = ? WHERE id = ?",
                HOJE.minusDays(1), resgate.getCupom().getId());
        jdbc.update("UPDATE produto SET nome = 'Fralda P' WHERE id = ?", resgate.getProdutoId());

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(200, resposta);
        JsonNode validado = corpo(resposta);
        assertEquals(0, new BigDecimal("10").compareTo(validado.get("desconto").decimalValue()));
        assertEquals(HOJE.plusDays(5).toString(), validado.get("validade").asText());
        assertEquals("Fralda RN", validado.get("produtoNome").asText());
        assertEquals("UTILIZADO", status(resgate));
    }

    @Test
    void promocaoDesativadaDepoisDoResgateAindaValida() {
        CupomResgatado resgate = resgateDePromocao(HOJE.plusDays(3));
        jdbc.update("UPDATE promocao SET ativo = FALSE, data_fim = ? WHERE id = ?", HOJE.minusDays(1),
                resgate.getPromocao().getId());

        assertStatus(200, validar(resgate));
        assertEquals("UTILIZADO", status(resgate));
    }

    // O app da gestante (meus cupons) passa a ver o UTILIZADO, com a mesma data de uso
    @Test
    void depoisDaValidacaoAGestanteVeUtilizadoComADataDeUso() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));
        JsonNode validado = corpo(validar(resgate));

        ResponseEntity<String> detalhe = http.exchange("/gestantes/cupons/{id}", HttpMethod.GET,
                new HttpEntity<>(autenticado(gestante)), String.class, resgate.getId());

        assertStatus(200, detalhe);
        JsonNode visto = corpo(detalhe);
        assertEquals("UTILIZADO", visto.get("status").asText());
        assertFalse(visto.get("dataUso").isNull());
        assertEquals(validado.get("dataUso").asText(), visto.get("dataUso").asText());
    }

    // ---- 404 ----

    @Test
    void codigoInexistenteResponde404ComOCodigoDeErro() throws Exception {
        ResponseEntity<String> resposta = validar("ZZZZ9999");

        assertStatus(404, resposta);
        assertEquals(json.readTree("""
                {"message":"Código não encontrado","codigo":"RESGATE_NAO_ENCONTRADO"}"""), corpo(resposta));
    }

    // O código de outra empresa responde exatamente como o inexistente, e não é tocado
    @Test
    void codigoDeOutraEmpresaRespondeIgualAoInexistente() throws Exception {
        CupomResgatado daOutra = resgateDeCupom(empresaAtiva(), HOJE.plusDays(5));

        ResponseEntity<String> resposta = validar(daOutra);
        ResponseEntity<String> inexistente = validar("ZZZZ9999");

        assertStatus(404, resposta);
        assertEquals(inexistente.getBody(), resposta.getBody());
        assertEquals("ATIVO", status(daOutra));
        assertEquals(0, comDataUso(daOutra));
    }

    // Vencido de outra empresa: também 404, e não vira EXPIRADO pela mão de quem não é a dona
    @Test
    void codigoVencidoDeOutraEmpresaResponde404ENaoExpira() {
        CupomResgatado daOutra = resgateDeCupom(empresaAtiva(), HOJE.minusDays(1));

        assertStatus(404, validar(daOutra));
        assertEquals("ATIVO", status(daOutra));
    }

    // ---- 409 ----

    // Validado de novo: 409 com a data do primeiro uso, a mesma que a primeira validação devolveu
    @Test
    void codigoJaUtilizadoResponde409ComADataDeUso() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));
        String dataUso = corpo(validar(resgate)).get("dataUso").asText();

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(409, resposta);
        JsonNode conflito = corpo(resposta);
        assertEquals("Código já utilizado", conflito.get("message").asText());
        assertEquals("RESGATE_JA_UTILIZADO", conflito.get("codigo").asText());
        assertEquals(dataUso, conflito.get("dataUso").asText());
        assertEquals(3, conflito.size());
        assertEquals("UTILIZADO", status(resgate));
    }

    // Já usado e depois vencido: continua respondendo "já utilizado", e o status não muda
    @Test
    void codigoUtilizadoEVencidoContinuaJaUtilizado() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));
        validar(resgate);
        jdbc.update("UPDATE cupom_resgatado SET data_validade = ? WHERE id = ?", HOJE.minusDays(3), resgate.getId());

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(409, resposta);
        assertEquals("RESGATE_JA_UTILIZADO", corpo(resposta).get("codigo").asText());
        assertEquals("UTILIZADO", status(resgate));
    }

    // Vencido e ainda ATIVO: grava EXPIRADO antes de responder. Sem dataUso no corpo
    @Test
    void codigoVencidoResponde409EGravaExpirado() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.minusDays(1));

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(409, resposta);
        assertEquals(json.readTree("""
                {"message":"Código expirado","codigo":"RESGATE_EXPIRADO"}"""), corpo(resposta));
        assertEquals("EXPIRADO", status(resgate));
        assertEquals(0, comDataUso(resgate));
    }

    @Test
    void promocaoVencidaResponde409EGravaExpirado() throws Exception {
        CupomResgatado resgate = resgateDePromocao(HOJE.minusDays(2));

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(409, resposta);
        assertEquals("RESGATE_EXPIRADO", corpo(resposta).get("codigo").asText());
        assertEquals("EXPIRADO", status(resgate));
    }

    // Já gravado como EXPIRADO (pela listagem da gestante, por exemplo): a mesma resposta
    @Test
    void codigoJaExpiradoResponde409Expirado() throws Exception {
        CupomResgatado resgate = resgateDeCupom(HOJE.minusDays(1));
        jdbc.update("UPDATE cupom_resgatado SET status = 'EXPIRADO' WHERE id = ?", resgate.getId());

        ResponseEntity<String> resposta = validar(resgate);

        assertStatus(409, resposta);
        assertEquals("RESGATE_EXPIRADO", corpo(resposta).get("codigo").asText());
        assertEquals("EXPIRADO", status(resgate));
    }

    // ---- 400 e 403 ----

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void codigoVazioResponde400(String codigo) {
        assertStatus(400, validar(codigo));
    }

    @Test
    void semCodigoResponde400() {
        assertStatus(400, validar(empresa, Map.of()));
    }

    // A gestante não valida o próprio código
    @Test
    void gestanteNaoValida() {
        CupomResgatado resgate = resgateDeCupom(HOJE.plusDays(5));

        assertStatus(403, validar(gestante, Map.of("codigo", resgate.getCodigo())));
        assertEquals("ATIVO", status(resgate));
        assertEquals(0, comDataUso(resgate));
    }

    // ---- Duas validações simultâneas ----

    // Algum UPDATE em cupom_resgatado parado esperando uma trava de linha: a prova de que a disputa aconteceu
    private boolean haUpdateDeResgateEsperandoTrava() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND wait_event_type = 'Lock' AND query ILIKE 'update cupom_resgatado%'", Integer.class) > 0;
    }

    private static void esperarAte(BooleanSupplier condicao) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_SEGUNDOS);
        while (!condicao.getAsBoolean()) {
            assertTrue(System.nanoTime() < limite, "a condição esperada não aconteceu");
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    // O caixa e outro caixa (ou o duplo clique) validam o mesmo código ao mesmo tempo. A disputa é forçada em toda
    // rodada: as duas requisições largam juntas; a primeira a passar do UPDATE (a linha já é UTILIZADO na transação
    // dela, ainda sem commit) fica parada na leitura da resposta até a outra estar esperando a trava dessa linha.
    // Liberada, a primeira comita e responde 200; o UPDATE da segunda reavalia o WHERE, já não casa, e ela responde 409
    // RESGATE_JA_UTILIZADO com a data de uso da primeira. Sem o UPDATE condicionado, as duas passariam
    @Test
    void duasValidacoesSimultaneasDoMesmoCodigoUmaPassa() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            reset(resgatadoRepository);
            CupomResgatado resgate = rodada % 2 == 0 ? resgateDeCupom(HOJE.plusDays(5))
                    : resgateDePromocao(HOJE.plusDays(5));
            AtomicInteger chegadas = new AtomicInteger();
            CountDownLatch liberar = new CountDownLatch(1);
            doAnswer(chamada -> {
                if (chegadas.incrementAndGet() == 1) {
                    assertTrue(liberar.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
                }
                // O spy de um repositório do Spring Data delega ao repositório real pela resposta padrão
                return mockingDetails(resgatadoRepository).getMockCreationSettings().getDefaultAnswer()
                        .answer(chamada);
            }).when(resgatadoRepository).daEmpresa(any(), any());

            Callable<ResponseEntity<String>> validacao = () -> validar(resgate);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<ResponseEntity<String>>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return validacao.call();
                }));
            }
            largada.countDown();
            // Falha a rodada se a disputa não acontecer: uma passou do UPDATE e está parada, e a outra espera a trava
            // dela, então nenhuma respondeu ainda
            esperarAte(() -> chegadas.get() == 1 && haUpdateDeResgateEsperandoTrava());
            liberar.countDown();
            List<ResponseEntity<String>> respostas = new ArrayList<>();
            for (Future<ResponseEntity<String>> futuro : futuros) {
                respostas.add(futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            }

            assertEquals(2, chegadas.get());
            assertEquals(List.of(200, 409), respostas.stream().map(r -> r.getStatusCode().value()).sorted().toList());
            JsonNode validado = corpo(respostas.stream().filter(r -> r.getStatusCode().value() == 200)
                    .findFirst().orElseThrow());
            JsonNode conflito = corpo(respostas.stream().filter(r -> r.getStatusCode().value() == 409)
                    .findFirst().orElseThrow());
            assertEquals("RESGATE_JA_UTILIZADO", conflito.get("codigo").asText());
            assertEquals(validado.get("dataUso").asText(), conflito.get("dataUso").asText());
            assertEquals("UTILIZADO", status(resgate));
            assertEquals(1, comDataUso(resgate));
            // Log de auditoria (RF18): uma linha RESGATE_VALIDADO só, a da validação que passou
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE cupom_resgatado_id = ?"
                    + " AND acao = 'RESGATE_VALIDADO'", Integer.class, resgate.getId()));
        }
    }
}
