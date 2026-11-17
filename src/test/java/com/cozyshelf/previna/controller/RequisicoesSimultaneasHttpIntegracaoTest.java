package com.cozyshelf.previna.controller;

import com.cozyshelf.previna.application.service.GeradorCodigoResgate;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.model.Usuario;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import com.cozyshelf.previna.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import static org.mockito.Mockito.reset;

// Duas requisições HTTP simultâneas de ponta a ponta: servidor de verdade numa porta aleatória, filtro JWT, controller,
// serviço e PostgreSQL. Prova que só uma das duas tem efeito. Os tokens saem do JwtService, como no login.
// Roda só com PREVINA_TEST_DB_URL (ver ResgateCupomIntegracaoTest e o README).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class RequisicoesSimultaneasHttpIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes
    private static final AtomicLong SEQUENCIA =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L + 300_000_000L);
    private static final int ESPERA_SEGUNDOS = 15;

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
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoSpyBean
    private UsuarioRepository usuarioRepository;
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
    private TipoAtendimentoRepository tipoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Medico medico;
    private ExecutorService executor;

    @BeforeEach
    void preparar() {
        reset(usuarioRepository);
        executor = Executors.newFixedThreadPool(4);
        long n = SEQUENCIA.incrementAndGet();
        medico = medicoRepository.save(new Medico("Dra. Ana", cpf(n), null, "CRM-" + n, "medico" + n, "hash", false));
    }

    @AfterEach
    void encerrar() {
        executor.shutdownNow();
    }

    // ---- Dados ----

    private static String cpf(long n) {
        return "%011d".formatted(n);
    }

    // Gestante ainda com a senha temporária: o token dela só conclui o primeiro acesso
    private Gestante gestantePendente() {
        long n = SEQUENCIA.incrementAndGet();
        return gestanteRepository.save(new Gestante("Maria", cpf(n), null, "gestante" + n, "hash",
                medico, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()));
    }

    // Gestante com o primeiro acesso concluído e o saldo indicado
    private Gestante gestanteAtivaComSaldo(int saldo) {
        Gestante gestante = gestantePendente();
        jdbc.update("UPDATE usuario SET status_conta = 'ATIVA' WHERE id = ?", gestante.getId());
        jdbc.update("UPDATE gestante SET saldo_pontos = ? WHERE usuario_id = ?", saldo, gestante.getId());
        return gestanteRepository.findById(gestante.getId()).orElseThrow();
    }

    private Cupom cupomDe(int pontos) {
        long n = SEQUENCIA.incrementAndGet();
        EmpresaParceira empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n),
                null, null, null, "empresa" + n, "hash"));
        Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null, Categoria.FRALDAS,
                null));
        return cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN, LocalDate.now().plusDays(5),
                pontos));
    }

    private Agendamento agendamentoDeHoje(Gestante gestante, int pontos) {
        TipoAtendimento tipo = tipoRepository.save(new TipoAtendimento(medico.getId(),
                "Exame " + SEQUENCIA.incrementAndGet(), CategoriaAtendimento.EXAME, pontos));
        return agendamentoRepository.save(new Agendamento(gestante, tipo, medico, LocalDate.now(), null));
    }

    private int saldo(Gestante gestante) {
        return jdbc.queryForObject("SELECT saldo_pontos FROM gestante WHERE usuario_id = ?", Integer.class,
                gestante.getId());
    }

    private int resgates(Gestante gestante) {
        return jdbc.queryForObject("SELECT count(*) FROM cupom_resgatado WHERE gestante_id = ?", Integer.class,
                gestante.getId());
    }

    private int debitos(Gestante gestante) {
        return jdbc.queryForObject("SELECT count(*) FROM transacao_pontos WHERE gestante_id = ? AND tipo = 'DEBITO'",
                Integer.class, gestante.getId());
    }

    // ---- HTTP ----

    private HttpHeaders autenticado(Usuario usuario) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(jwtService.gerarToken(usuario));
        return cabecalhos;
    }

    private Callable<ResponseEntity<String>> resgatar(Gestante gestante, Cupom cupom, String chave) {
        HttpHeaders cabecalhos = autenticado(gestante);
        if (chave != null) {
            cabecalhos.set("Idempotency-Key", chave);
        }
        return () -> http.exchange("/cupons/{id}/resgatar", HttpMethod.POST, new HttpEntity<>(cabecalhos),
                String.class, cupom.getId());
    }

    private Callable<ResponseEntity<String>> resolver(Agendamento agendamento, String acao) {
        HttpHeaders cabecalhos = autenticado(medico);
        return () -> http.exchange("/agendamentos/{id}/" + acao, HttpMethod.PUT, new HttpEntity<>(cabecalhos),
                String.class, agendamento.getId());
    }

    // Cadastro pelo portal: o mesmo tipo em cada uma das datas
    private Callable<ResponseEntity<String>> agendar(Gestante gestante, TipoAtendimento tipo, LocalDate... datas) {
        HttpHeaders cabecalhos = autenticado(medico);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        List<Map<String, Object>> itens = java.util.Arrays.stream(datas)
                .map(data -> Map.<String, Object>of("tipoAtendimento", tipo.getId(), "dataPrevista", data.toString()))
                .toList();
        Map<String, Object> corpo = Map.of("gestante", gestante.getId(), "itens", itens);
        return () -> http.exchange("/agendamentos", HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos),
                String.class);
    }

    private Callable<ResponseEntity<String>> concluirPrimeiroAcesso(Gestante gestante, String login, String senha) {
        HttpHeaders cabecalhos = autenticado(gestante);
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> campos = new LinkedMultiValueMap<>();
        campos.add("novoLogin", login);
        campos.add("novaSenha", senha);
        campos.add("consentimentoLgpd", "true");
        return () -> http.exchange("/gestantes/primeiro-acesso", HttpMethod.PUT, new HttpEntity<>(campos, cabecalhos),
                String.class);
    }

    // Dispara as requisições juntas (largada única) e espera as respostas
    private List<ResponseEntity<String>> aoMesmoTempo(List<Callable<ResponseEntity<String>>> requisicoes,
                                                      Runnable enquantoEsperam) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futuros = new ArrayList<>();
        for (Callable<ResponseEntity<String>> requisicao : requisicoes) {
            futuros.add(executor.submit(() -> {
                largada.await();
                return requisicao.call();
            }));
        }
        largada.countDown();
        enquantoEsperam.run();
        List<ResponseEntity<String>> respostas = new ArrayList<>();
        for (Future<ResponseEntity<String>> futuro : futuros) {
            respostas.add(futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
        }
        return respostas;
    }

    private List<ResponseEntity<String>> aoMesmoTempo(List<Callable<ResponseEntity<String>>> requisicoes)
            throws Exception {
        return aoMesmoTempo(requisicoes, () -> { });
    }

    private JsonNode corpo(ResponseEntity<String> resposta) throws Exception {
        return json.readTree(resposta.getBody());
    }

    private static List<Integer> status(List<ResponseEntity<String>> respostas) {
        return respostas.stream().map(r -> r.getStatusCode().value()).sorted().toList();
    }

    private static ResponseEntity<String> comStatus(List<ResponseEntity<String>> respostas, int status) {
        return respostas.stream().filter(r -> r.getStatusCode().value() == status).findFirst().orElseThrow();
    }

    // ---- Resgate de cupom ----

    // Com a mesma chave, a segunda requisição espera na trava da gestante e encontra o resgate da primeira.
    // Prova o efeito único pelo HTTP qualquer que seja a ordem em que as duas chegam
    @Test
    void doisPostsSimultaneosComAMesmaChaveResgatamUmaVezSo() throws Exception {
        Gestante gestante = gestanteAtivaComSaldo(100);
        Cupom cupom = cupomDe(30);
        String chave = UUID.randomUUID().toString();

        var respostas = aoMesmoTempo(List.of(resgatar(gestante, cupom, chave), resgatar(gestante, cupom, chave)));

        assertEquals(List.of(200, 201), status(respostas));
        JsonNode criado = corpo(comStatus(respostas, 201));
        JsonNode repetido = corpo(comStatus(respostas, 200));
        assertEquals(criado.get("codigo").asText(), repetido.get("codigo").asText());
        assertEquals(criado.get("id").asLong(), repetido.get("id").asLong());
        assertEquals(70, criado.get("saldo").asInt());
        assertEquals(70, repetido.get("saldo").asInt());
        assertEquals(70, saldo(gestante));
        assertEquals(1, resgates(gestante));
        assertEquals(1, debitos(gestante));
    }

    @Test
    void doisPostsSimultaneosComChavesDiferentesSaoDoisResgates() throws Exception {
        Gestante gestante = gestanteAtivaComSaldo(100);
        Cupom cupom = cupomDe(30);

        var respostas = aoMesmoTempo(List.of(resgatar(gestante, cupom, UUID.randomUUID().toString()),
                resgatar(gestante, cupom, UUID.randomUUID().toString())));

        assertEquals(List.of(201, 201), status(respostas));
        assertNotEquals(corpo(respostas.get(0)).get("codigo").asText(), corpo(respostas.get(1)).get("codigo").asText());
        assertEquals(40, saldo(gestante));
        assertEquals(2, resgates(gestante));
        assertEquals(2, debitos(gestante));
    }

    // Sem chave (versões antigas do app) e com saldo para um só: a trava da gestante faz a segunda conferir o saldo
    // já debitado. Prova que só um resgate passa e o saldo não fica negativo, qualquer que seja a ordem
    @Test
    void doisPostsSimultaneosSemChaveComSaldoParaUmSo() throws Exception {
        Gestante gestante = gestanteAtivaComSaldo(100);
        Cupom cupom = cupomDe(60);

        var respostas = aoMesmoTempo(List.of(resgatar(gestante, cupom, null), resgatar(gestante, cupom, null)));

        assertEquals(List.of(201, 409), status(respostas));
        JsonNode conflito = corpo(comStatus(respostas, 409));
        assertEquals("Pontos insuficientes", conflito.get("message").asText());
        assertEquals("PONTOS_INSUFICIENTES", conflito.get("codigo").asText());
        assertEquals(40, saldo(gestante));
        assertEquals(1, resgates(gestante));
        assertEquals(1, debitos(gestante));
    }

    // ---- Resgate de promoção (V22: índice cupom_resgatado_promocao_uma_vez) ----

    private EmpresaParceira empresaAtiva() {
        long n = SEQUENCIA.incrementAndGet();
        EmpresaParceira empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n),
                null, null, null, "empresa" + n, "hash"));
        jdbc.update("UPDATE usuario SET status_conta = 'ATIVA' WHERE id = ?", empresa.getId());
        return empresaRepository.findById(empresa.getId()).orElseThrow();
    }

    private Promocao promocaoVigente() {
        long n = SEQUENCIA.incrementAndGet();
        EmpresaParceira empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n),
                null, null, null, "empresa" + n, "hash"));
        return promocaoRepository.save(new Promocao(empresa.getId(), "Semana do bebê", null, new BigDecimal("15"),
                LocalDate.now(), LocalDate.now().plusDays(5), Categoria.FRALDAS));
    }

    private Callable<ResponseEntity<String>> resgatarPromocao(Gestante gestante, Promocao promocao, String chave) {
        HttpHeaders cabecalhos = autenticado(gestante);
        if (chave != null) {
            cabecalhos.set("Idempotency-Key", chave);
        }
        return () -> http.exchange("/promocoes/{id}/resgatar", HttpMethod.POST, new HttpEntity<>(cabecalhos),
                String.class, promocao.getId());
    }

    private Callable<ResponseEntity<String>> cadastrarPromocao(EmpresaParceira empresa, LocalDate inicio,
                                                               LocalDate fim) {
        HttpHeaders cabecalhos = autenticado(empresa);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> corpo = Map.of("titulo", "Semana do bebê", "desconto", 15,
                "dataInicio", inicio.toString(), "dataFim", fim.toString(), "categoria", "FRALDAS");
        return () -> http.exchange("/empresas/promocoes", HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos),
                String.class);
    }

    // Duplo toque com chaves diferentes: um 201 e um 409 "Promoção já resgatada", uma linha e o saldo intacto.
    // Algumas rodadas, porque a ordem de chegada não é forçada
    @Test
    void doisPostsSimultaneosDaMesmaPromocaoComChavesDiferentesResgatamUmaVez() throws Exception {
        for (int rodada = 0; rodada < 3; rodada++) {
            Gestante gestante = gestanteAtivaComSaldo(70);
            Promocao promocao = promocaoVigente();

            var respostas = aoMesmoTempo(List.of(
                    resgatarPromocao(gestante, promocao, UUID.randomUUID().toString()),
                    resgatarPromocao(gestante, promocao, UUID.randomUUID().toString())));

            assertEquals(List.of(201, 409), status(respostas));
            JsonNode conflito = corpo(comStatus(respostas, 409));
            assertEquals("Promoção já resgatada", conflito.get("message").asText());
            assertEquals("PROMOCAO_JA_RESGATADA", conflito.get("codigo").asText());
            JsonNode criado = corpo(comStatus(respostas, 201));
            assertEquals("PROMOCAO", criado.get("tipo").asText());
            assertEquals(0, criado.get("pontosGastos").asInt());
            assertEquals(70, criado.get("saldo").asInt());
            assertEquals(70, saldo(gestante));
            assertEquals(1, resgates(gestante));
            assertEquals(0, debitos(gestante));
        }
    }

    @Test
    void doisPostsSimultaneosDaMesmaPromocaoComAMesmaChaveDevolvemOMesmoResgate() throws Exception {
        for (int rodada = 0; rodada < 3; rodada++) {
            Gestante gestante = gestanteAtivaComSaldo(0);
            Promocao promocao = promocaoVigente();
            String chave = UUID.randomUUID().toString();

            var respostas = aoMesmoTempo(List.of(resgatarPromocao(gestante, promocao, chave),
                    resgatarPromocao(gestante, promocao, chave)));

            assertEquals(List.of(200, 201), status(respostas));
            assertEquals(corpo(comStatus(respostas, 201)).get("codigo").asText(),
                    corpo(comStatus(respostas, 200)).get("codigo").asText());
            assertEquals(1, resgates(gestante));
        }
    }

    // Duplo clique no portal com períodos que se cruzam: a exclusão da V23 deixa uma ativa só
    @Test
    void doisCadastrosSimultaneosDePromocoesSobrepostasGravamUma() throws Exception {
        for (int rodada = 0; rodada < 3; rodada++) {
            EmpresaParceira empresa = empresaAtiva();
            LocalDate hoje = LocalDate.now();

            var respostas = aoMesmoTempo(List.of(cadastrarPromocao(empresa, hoje, hoje.plusDays(7)),
                    cadastrarPromocao(empresa, hoje.plusDays(7), hoje.plusDays(14))));

            assertEquals(List.of(201, 409), status(respostas));
            JsonNode conflito = corpo(comStatus(respostas, 409));
            assertEquals("Já existe promoção ativa dessa categoria nesse período", conflito.get("message").asText());
            assertEquals("PROMOCAO_SOBREPOSTA", conflito.get("codigo").asText());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promocao WHERE empresa_id = ?", Integer.class,
                    empresa.getId()));
        }
    }

    // ---- Meus cupons: expiração na leitura ----

    @Autowired
    private CupomResgatadoRepository cupomResgatadoRepository;
    @Autowired
    private GeradorCodigoResgate geradorCodigo;

    // Resgate do cupom pela gestante, já com a validade e o status indicados
    private CupomResgatado resgateValidoAte(Gestante gestante, Cupom cupom, LocalDate validade, String status) {
        EmpresaParceira empresa = empresaRepository.findById(cupom.getEmpresaId()).orElseThrow();
        CupomResgatado resgate = cupomResgatadoRepository.save(new CupomResgatado(cupom, empresa, gestante,
                geradorCodigo.gerarCodigoUnico(), null));
        jdbc.update("UPDATE cupom_resgatado SET data_validade = ?, status = ? WHERE id = ?", validade, status,
                resgate.getId());
        return resgate;
    }

    private String statusDoResgate(CupomResgatado resgate) {
        return jdbc.queryForObject("SELECT status FROM cupom_resgatado WHERE id = ?", String.class, resgate.getId());
    }

    private Callable<ResponseEntity<String>> meusCupons(Gestante gestante) {
        HttpHeaders cabecalhos = autenticado(gestante);
        return () -> http.exchange("/gestantes/cupons", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    // Duas aberturas de "meus cupons" ao mesmo tempo, com vencidos a expirar: as duas respondem 200 já com EXPIRADO
    // (a segunda espera a trava de linha da primeira e o UPDATE condicionado a ATIVO não casa mais), sem deadlock nem
    // 500, e o UTILIZADO vencido continua UTILIZADO. Algumas rodadas, porque a ordem de chegada não é forçada
    @Test
    void duasListagensSimultaneasDosCuponsExpiramOsVencidosSemErro() throws Exception {
        for (int rodada = 0; rodada < 3; rodada++) {
            Gestante gestante = gestanteAtivaComSaldo(0);
            Cupom cupom = cupomDe(30);
            List<CupomResgatado> vencidos = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                vencidos.add(resgateValidoAte(gestante, cupom, LocalDate.now().minusDays(1 + i), "ATIVO"));
            }
            CupomResgatado utilizadoVencido = resgateValidoAte(gestante, cupom, LocalDate.now().minusDays(2),
                    "UTILIZADO");
            CupomResgatado valido = resgateValidoAte(gestante, cupom, LocalDate.now().plusDays(1), "ATIVO");

            var respostas = aoMesmoTempo(List.of(meusCupons(gestante), meusCupons(gestante)));

            assertEquals(List.of(200, 200), status(respostas));
            for (ResponseEntity<String> resposta : respostas) {
                JsonNode pagina = corpo(resposta);
                assertEquals(8, pagina.get("total").asInt());
                JsonNode itens = pagina.get("itens");
                assertEquals(valido.getId(), itens.get(0).get("id").asLong());
                assertEquals("ATIVO", itens.get(0).get("status").asText());
                for (int i = 1; i < itens.size(); i++) {
                    assertNotEquals("ATIVO", itens.get(i).get("status").asText());
                }
            }
            for (CupomResgatado vencido : vencidos) {
                assertEquals("EXPIRADO", statusDoResgate(vencido));
            }
            assertEquals("UTILIZADO", statusDoResgate(utilizadoVencido));
            assertEquals("ATIVO", statusDoResgate(valido));
        }
    }

    // ---- Confirmação de agendamento ----

    // Confirmar e registrar não comparecimento juntos: a trava do agendamento deixa um desfecho só. Quem vence varia;
    // o teste prova que sai um 200 e um 409 e que o crédito existe se e só se o desfecho foi a realização.
    // Algumas rodadas, porque a ordem de chegada não é forçada
    @Test
    void confirmarENaoCompareceuSimultaneosTemUmDesfechoSo() throws Exception {
        for (int rodada = 0; rodada < 3; rodada++) {
            Gestante gestante = gestanteAtivaComSaldo(0);
            Agendamento agendamento = agendamentoDeHoje(gestante, 40);

            var respostas = aoMesmoTempo(List.of(resolver(agendamento, "confirmar"),
                    resolver(agendamento, "nao-compareceu")));

            assertEquals(List.of(200, 409), status(respostas));
            JsonNode conflito = corpo(comStatus(respostas, 409));
            assertEquals("Este agendamento já foi resolvido", conflito.get("message").asText());
            // Conflito sem código próprio: o corpo não traz o campo
            assertFalse(conflito.has("codigo"));
            String statusFinal = jdbc.queryForObject("SELECT status FROM agendamento WHERE id = ?", String.class,
                    agendamento.getId());
            int creditos = jdbc.queryForObject("SELECT count(*) FROM transacao_pontos WHERE agendamento_id = ?",
                    Integer.class, agendamento.getId());
            assertTrue(creditos <= 1);
            assertEquals("REALIZADO".equals(statusFinal) ? 1 : 0, creditos);
            assertEquals("REALIZADO".equals(statusFinal) ? 40 : 0, saldo(gestante));
        }
    }

    // ---- Cadastro de agendamento ----

    private static final String PENDENTE_REPETIDO = "Essa gestante já tem esse exame ou consulta pendente nessa data";

    private TipoAtendimento tipo() {
        return tipoRepository.save(new TipoAtendimento(medico.getId(), "Exame " + SEQUENCIA.incrementAndGet(),
                CategoriaAtendimento.EXAME, 25));
    }

    private int pendentes(Gestante gestante) {
        return jdbc.queryForObject("SELECT count(*) FROM agendamento WHERE gestante_id = ? AND status = 'AGENDADO'",
                Integer.class, gestante.getId());
    }

    // Duplo clique no portal: o índice parcial da V21 deixa um pendente só, e o segundo POST recebe 409 com a
    // mensagem que o portal exibe. Algumas rodadas, porque a ordem de chegada não é forçada
    @Test
    void doisCadastrosSimultaneosIguaisGravamUmPendenteSo() throws Exception {
        for (int rodada = 0; rodada < 3; rodada++) {
            Gestante gestante = gestanteAtivaComSaldo(0);
            TipoAtendimento tipo = tipo();
            LocalDate data = LocalDate.now().plusDays(3);

            var respostas = aoMesmoTempo(List.of(agendar(gestante, tipo, data), agendar(gestante, tipo, data)));

            assertEquals(List.of(201, 409), status(respostas));
            JsonNode conflito = corpo(comStatus(respostas, 409));
            assertEquals(PENDENTE_REPETIDO, conflito.get("message").asText());
            assertEquals("AGENDAMENTO_PENDENTE_REPETIDO", conflito.get("codigo").asText());
            assertEquals(1, pendentes(gestante));
        }
    }

    @Test
    void itemRepetidoNoMesmoCadastroResponde409ENaoGravaNada() throws Exception {
        Gestante gestante = gestanteAtivaComSaldo(0);
        TipoAtendimento tipo = tipo();
        LocalDate data = LocalDate.now().plusDays(3);

        ResponseEntity<String> resposta = agendar(gestante, tipo, data, data).call();

        assertEquals(409, resposta.getStatusCode().value());
        assertEquals(PENDENTE_REPETIDO, corpo(resposta).get("message").asText());
        assertEquals("AGENDAMENTO_PENDENTE_REPETIDO", corpo(resposta).get("codigo").asText());
        assertEquals(0, pendentes(gestante));
    }

    // ---- Primeiro acesso ----

    private boolean haTransacaoEsperandoTrava() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity"
                + " WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class) > 0;
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

    // A intercalação é forçada: quem chega à conferência do login (já com a conta lida, antes de gravar) espera até a
    // outra requisição chegar também (sem a trava, as duas passavam e as duas concluíam) ou ficar parada na trava da
    // conta. Assim o teste falha sem o SELECT ... FOR UPDATE, e não passa por sorte
    @Test
    void doisPrimeirosAcessosSimultaneosConcluemUmSo() throws Exception {
        Gestante gestante = gestantePendente();
        long n = SEQUENCIA.incrementAndGet();
        AtomicInteger chegadas = new AtomicInteger();
        CountDownLatch liberar = new CountDownLatch(1);
        doAnswer(chamada -> {
            chegadas.incrementAndGet();
            assertTrue(liberar.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            // O spy de um repositório do Spring Data não tem método real a chamar
            return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM usuario WHERE login = ?)", Boolean.class,
                    chamada.<String>getArgument(0));
        }).when(usuarioRepository).existsByLogin(any());

        var respostas = aoMesmoTempo(List.of(
                concluirPrimeiroAcesso(gestante, "maria.a" + n, "senhaDaA1"),
                concluirPrimeiroAcesso(gestante, "maria.b" + n, "senhaDaB2")), () -> {
            esperarAte(() -> chegadas.get() == 2 || chegadas.get() == 1 && haTransacaoEsperandoTrava());
            liberar.countDown();
        });

        assertEquals(List.of(200, 409), status(respostas));
        assertEquals("Primeiro acesso já concluído", corpo(comStatus(respostas, 409)).get("message").asText());
        // Ficam as credenciais de quem recebeu 200
        boolean venceuA = respostas.get(0).getStatusCode().value() == 200;
        Map<String, Object> conta = jdbc.queryForMap("SELECT login, senha_hash, status_conta FROM usuario WHERE id = ?",
                gestante.getId());
        assertEquals(venceuA ? "maria.a" + n : "maria.b" + n, conta.get("login"));
        assertTrue(passwordEncoder.matches(venceuA ? "senhaDaA1" : "senhaDaB2", (String) conta.get("senha_hash")));
        assertEquals("ATIVA", conta.get("status_conta"));
    }
}
