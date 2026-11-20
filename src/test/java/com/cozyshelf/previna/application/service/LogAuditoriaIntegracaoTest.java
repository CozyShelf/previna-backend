package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.request.ItemAgendamentoRequest;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.application.exception.CodigoErro;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.LogAuditoriaRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

// Log de auditoria (RF18, V24) contra um PostgreSQL de verdade: cada operação grava exatamente uma linha com os campos
// certos, os erros e a repetição idempotente não gravam, uma falha ao gravar o log desfaz a operação inteira, duas
// confirmações simultâneas do mesmo agendamento geram um registro só e o gatilho barra UPDATE e DELETE.
// Roda só com PREVINA_TEST_DB_URL (ver ResgateCupomIntegracaoTest e o README).
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class LogAuditoriaIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes (e o log nem poderia ser: o gatilho barra o DELETE).
    // Cada teste lê só as linhas da própria gestante
    private static final AtomicLong SEQUENCIA =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L + 600_000_000L);
    private static final int ESPERA_SEGUNDOS = 15;
    private static final LocalDate HOJE = LocalDate.now();

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private AgendamentoService agendamentoService;
    @Autowired
    private CupomService cupomService;
    @Autowired
    private PromocaoService promocaoService;
    @Autowired
    private ResgateService resgateService;
    @Autowired
    private Auditoria auditoria;
    @MockitoSpyBean
    private LogAuditoriaRepository logRepository;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private GestanteRepository gestanteRepository;
    @Autowired
    private TipoAtendimentoRepository tipoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
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

    private Medico medico;
    private Gestante gestante;
    private EmpresaParceira empresa;
    private ExecutorService executor;

    @BeforeEach
    void preparar() {
        reset(logRepository);
        executor = Executors.newFixedThreadPool(2);
        long n = SEQUENCIA.incrementAndGet();
        medico = medicoRepository.save(new Medico("Dra. Ana", cpf(n), null, "CRM-L" + n, "medlog" + n, "hash", false));
        n = SEQUENCIA.incrementAndGet();
        gestante = gestanteRepository.save(new Gestante("Maria", cpf(n), null, "geslog" + n, "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", HOJE));
        n = SEQUENCIA.incrementAndGet();
        // Uma empresa por teste: a exclusão da V23 não deixa duas promoções ativas da mesma categoria e período
        empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n), null, null, null,
                "emplog" + n, "hash"));
    }

    @AfterEach
    void encerrar() {
        executor.shutdownNow();
    }

    // ---- Dados ----

    private static String cpf(long n) {
        return "%011d".formatted(n);
    }

    private void comSaldo(int pontos) {
        jdbc.update("UPDATE gestante SET saldo_pontos = ? WHERE usuario_id = ?", pontos, gestante.getId());
    }

    private TipoAtendimento tipo(int pontos) {
        return tipoRepository.save(new TipoAtendimento(medico.getId(), "Exame " + SEQUENCIA.incrementAndGet(),
                CategoriaAtendimento.EXAME, pontos));
    }

    // Já pode ser resolvido: a data prevista é hoje
    private Agendamento agendamentoDeHoje(int pontos) {
        return agendamentoRepository.save(new Agendamento(gestante, tipo(pontos), medico, HOJE, null));
    }

    private Cupom cupom(int pontos) {
        Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null, Categoria.FRALDAS,
                new BigDecimal("39.90")));
        return cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN, HOJE.plusDays(5), pontos));
    }

    private Promocao promocao() {
        return promocaoRepository.save(new Promocao(empresa.getId(), "Semana do bebê", null, new BigDecimal("15"),
                HOJE.minusDays(1), HOJE.plusDays(5), Categoria.FRALDAS));
    }

    // ---- Leitura do banco ----

    private List<Map<String, Object>> logs() {
        return jdbc.queryForList("SELECT * FROM log_auditoria WHERE gestante_id = ? ORDER BY id", gestante.getId());
    }

    // A única linha do log desta gestante
    private Map<String, Object> logUnico() {
        List<Map<String, Object>> linhas = logs();
        assertEquals(1, linhas.size(), linhas.toString());
        return linhas.getFirst();
    }

    private int logs(String acao) {
        return jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE gestante_id = ? AND acao = ?",
                Integer.class, gestante.getId(), acao);
    }

    private int saldo() {
        return jdbc.queryForObject("SELECT saldo_pontos FROM gestante WHERE usuario_id = ?", Integer.class,
                gestante.getId());
    }

    private int transacoes() {
        return jdbc.queryForObject("SELECT count(*) FROM transacao_pontos WHERE gestante_id = ?", Integer.class,
                gestante.getId());
    }

    private int resgates() {
        return jdbc.queryForObject("SELECT count(*) FROM cupom_resgatado WHERE gestante_id = ?", Integer.class,
                gestante.getId());
    }

    private Map<String, Object> agendamento(Long id) {
        return jdbc.queryForMap("SELECT status, data_confirmacao FROM agendamento WHERE id = ?", id);
    }

    private Map<String, Object> resgate(Long id) {
        return jdbc.queryForMap("SELECT status, data_resgate, data_uso FROM cupom_resgatado WHERE id = ?", id);
    }

    private static Long id(Map<String, Object> linha, String coluna) {
        Object valor = linha.get(coluna);
        return valor == null ? null : ((Number) valor).longValue();
    }

    private static LocalDateTime hora(Map<String, Object> linha, String coluna) {
        return ((java.sql.Timestamp) linha.get(coluna)).toLocalDateTime();
    }

    // A linha do log com as colunas esperadas; pontos e as referências não informadas têm de ser nulas
    private static void assertLog(Map<String, Object> log, String acao, Long usuario, Long gestante,
                                  Long agendamento, Long transacao, Long resgate, Integer pontos) {
        assertEquals(acao, log.get("acao"));
        assertEquals(usuario, id(log, "usuario_id"));
        assertEquals(gestante, id(log, "gestante_id"));
        assertEquals(agendamento, id(log, "agendamento_id"));
        assertEquals(transacao, id(log, "transacao_pontos_id"));
        assertEquals(resgate, id(log, "cupom_resgatado_id"));
        assertEquals(pontos, log.get("pontos"));
        assertNotNull(log.get("data_hora"));
    }

    // Faz o save do log falhar, como se o INSERT tivesse sido recusado pelo banco
    private void logFalha() {
        doThrow(new DataIntegrityViolationException("falha simulada no log")).when(logRepository).save(any());
        doThrow(new DataIntegrityViolationException("falha simulada no log")).when(logRepository).saveAll(any());
    }

    // ---- Agendamento criado ----

    @Test
    void cadastroGravaUmaLinhaPorAgendamento() {
        TipoAtendimento hemograma = tipo(25);
        TipoAtendimento consulta = tipo(40);

        List<AgendamentoResponse> criados = agendamentoService.agendar(new AgendamentoRequest(gestante.getId(),
                List.of(new ItemAgendamentoRequest(hemograma.getId(), HOJE.plusDays(3), null),
                        new ItemAgendamentoRequest(consulta.getId(), HOJE.plusDays(7), null))), medico.getId());

        List<Map<String, Object>> linhas = logs();
        assertEquals(2, linhas.size());
        for (int i = 0; i < 2; i++) {
            assertLog(linhas.get(i), "AGENDAMENTO_CRIADO", medico.getId(), gestante.getId(), criados.get(i).id(),
                    null, null, null);
        }
        // O cadastro é uma operação só: as linhas têm a mesma hora (a fonte da hora é conferida no
        // AgendamentoServiceTest; aqui a leitura pelo JdbcTemplate não passa pelo fuso do Hibernate)
        assertEquals(linhas.get(0).get("data_hora"), linhas.get(1).get("data_hora"));
    }

    @Test
    void cadastroRecusadoNaoGravaLog() {
        TipoAtendimento hemograma = tipo(25);
        ItemAgendamentoRequest item = new ItemAgendamentoRequest(hemograma.getId(), HOJE.plusDays(3), null);

        // O mesmo pendente duas vezes no pedido: o índice da V21 recusa (409) e nada é gravado
        assertThrows(ConflitoException.class, () -> agendamentoService.agendar(
                new AgendamentoRequest(gestante.getId(), List.of(item, item)), medico.getId()));
        // Tipo inexistente: 400
        assertThrows(DadosInvalidosException.class, () -> agendamentoService.agendar(
                new AgendamentoRequest(gestante.getId(), List.of(new ItemAgendamentoRequest(-1L, HOJE, null))),
                medico.getId()));

        assertEquals(List.of(), logs());
    }

    @Test
    void falhaNoLogDesfazOCadastro() {
        TipoAtendimento hemograma = tipo(25);
        logFalha();

        assertThrows(DataIntegrityViolationException.class, () -> agendamentoService.agendar(new AgendamentoRequest(
                gestante.getId(), List.of(new ItemAgendamentoRequest(hemograma.getId(), HOJE, null))),
                medico.getId()));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM agendamento WHERE gestante_id = ?", Integer.class,
                gestante.getId()));
    }

    // ---- Agendamento realizado e não comparecido ----

    @Test
    void confirmacaoGravaUmaLinhaComOCreditoEOsPontos() {
        Agendamento exame = agendamentoDeHoje(30);

        agendamentoService.confirmar(exame.getId(), medico.getId());

        Long credito = jdbc.queryForObject("SELECT id FROM transacao_pontos WHERE agendamento_id = ?", Long.class,
                exame.getId());
        Map<String, Object> log = logUnico();
        assertLog(log, "AGENDAMENTO_REALIZADO", medico.getId(), gestante.getId(), exame.getId(), credito, null, 30);
        // A hora da própria operação: a mesma gravada na confirmação
        assertEquals(hora(agendamento(exame.getId()), "data_confirmacao"), hora(log, "data_hora"));
    }

    @Test
    void naoComparecimentoGravaUmaLinhaSemPontos() {
        Agendamento exame = agendamentoDeHoje(30);

        agendamentoService.registrarNaoComparecimento(exame.getId(), medico.getId());

        Map<String, Object> log = logUnico();
        assertLog(log, "AGENDAMENTO_NAO_COMPARECEU", medico.getId(), gestante.getId(), exame.getId(), null, null,
                null);
        assertEquals(hora(agendamento(exame.getId()), "data_confirmacao"), hora(log, "data_hora"));
    }

    @Test
    void desfechoRecusadoNaoGravaLog() {
        Agendamento resolvido = agendamentoDeHoje(30);
        agendamentoService.confirmar(resolvido.getId(), medico.getId());
        Agendamento futuro = agendamentoRepository.save(new Agendamento(gestante, tipo(10), medico,
                HOJE.plusDays(2), null));
        long outroMedico = medicoRepository.save(new Medico("Dr. Bruno", cpf(SEQUENCIA.incrementAndGet()), null,
                "CRM-L" + SEQUENCIA.incrementAndGet(), "medlog" + SEQUENCIA.incrementAndGet(), "hash", false)).getId();
        Agendamento pendente = agendamentoDeHoje(15);

        // Já resolvido, antes da data prevista e fora do alcance do médico: 409, 409 e 404
        assertThrows(ConflitoException.class, () -> agendamentoService.confirmar(resolvido.getId(), medico.getId()));
        assertThrows(ConflitoException.class,
                () -> agendamentoService.registrarNaoComparecimento(resolvido.getId(), medico.getId()));
        assertThrows(ConflitoException.class, () -> agendamentoService.confirmar(futuro.getId(), medico.getId()));
        assertThrows(NaoEncontradoException.class, () -> agendamentoService.confirmar(pendente.getId(), outroMedico));

        // Só a linha da primeira confirmação
        assertEquals(1, logs().size());
        assertEquals(1, logs("AGENDAMENTO_REALIZADO"));
    }

    @Test
    void falhaNoLogDesfazAConfirmacaoEOCredito() {
        Agendamento exame = agendamentoDeHoje(30);
        logFalha();

        assertThrows(DataIntegrityViolationException.class,
                () -> agendamentoService.confirmar(exame.getId(), medico.getId()));

        assertEquals("AGENDADO", agendamento(exame.getId()).get("status"));
        assertEquals(0, transacoes());
        assertEquals(0, saldo());
        assertEquals(List.of(), logs());
    }

    @Test
    void falhaNoLogDesfazONaoComparecimento() {
        Agendamento exame = agendamentoDeHoje(30);
        logFalha();

        assertThrows(DataIntegrityViolationException.class,
                () -> agendamentoService.registrarNaoComparecimento(exame.getId(), medico.getId()));

        assertEquals("AGENDADO", agendamento(exame.getId()).get("status"));
    }

    // Duas confirmações simultâneas do mesmo agendamento, com a disputa forçada em toda rodada: a primeira a pegar a
    // trava do agendamento (findParaResolver) fica parada na gravação do log, com o crédito já feito e sem commit, até
    // a outra estar esperando essa trava no banco (pg_stat_activity). Liberada, a primeira comita; a segunda relê o
    // agendamento já REALIZADO e responde 409. Um crédito e uma linha AGENDAMENTO_REALIZADO
    @Test
    void duasConfirmacoesSimultaneasGravamUmCreditoEUmaLinha() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            reset(logRepository);
            Agendamento exame = agendamentoDeHoje(30);
            AtomicInteger chegadas = new AtomicInteger();
            CountDownLatch liberar = new CountDownLatch(1);
            doAnswer(chamada -> {
                if (chegadas.incrementAndGet() == 1) {
                    assertTrue(liberar.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
                }
                // O spy de um repositório do Spring Data delega ao repositório real pela resposta padrão
                return mockingDetails(logRepository).getMockCreationSettings().getDefaultAnswer().answer(chamada);
            }).when(logRepository).save(any());

            CountDownLatch largada = new CountDownLatch(1);
            List<Future<ConfirmacaoResponse>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return agendamentoService.confirmar(exame.getId(), medico.getId());
                }));
            }
            largada.countDown();
            // Falha a rodada se a disputa não acontecer: uma está parada no log, e a outra espera a trava dela
            esperarAte(() -> chegadas.get() == 1 && haLeituraDoAgendamentoEsperandoTrava());
            liberar.countDown();

            List<String> desfechos = new ArrayList<>();
            for (Future<ConfirmacaoResponse> futuro : futuros) {
                desfechos.add(desfecho(futuro));
            }
            assertEquals(1, chegadas.get());
            assertTrue(desfechos.contains(null), desfechos.toString());
            assertTrue(desfechos.contains("Este agendamento já foi resolvido"), desfechos.toString());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transacao_pontos WHERE agendamento_id = ?",
                    Integer.class, exame.getId()));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE agendamento_id = ?"
                    + " AND acao = 'AGENDAMENTO_REALIZADO'", Integer.class, exame.getId()));
        }
        assertEquals(150, saldo());
    }

    // O SELECT ... FOR UPDATE do agendamento parado esperando a trava de linha
    private boolean haLeituraDoAgendamentoEsperandoTrava() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND wait_event_type = 'Lock' AND query ILIKE '%from agendamento%for%update%'", Integer.class) > 0;
    }

    private static void esperarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_SEGUNDOS);
        while (!condicao.getAsBoolean()) {
            assertTrue(System.nanoTime() < limite, "a condição esperada não aconteceu");
            Thread.sleep(20);
        }
    }

    // null se deu certo, ou a mensagem da ConflitoException
    private static String desfecho(Future<?> futuro) throws Exception {
        try {
            futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException erro) {
            if (erro.getCause() instanceof ConflitoException conflito) {
                return conflito.getMessage();
            }
            throw erro;
        }
    }

    // ---- Cupom resgatado ----

    @Test
    void resgateDeCupomGravaUmaLinhaComODebito() {
        comSaldo(100);
        Cupom cupom = cupom(60);

        CupomService.Resgate resgate = cupomService.resgatar(cupom.getId(), gestante.getId(), UUID.randomUUID());

        Long debito = jdbc.queryForObject("SELECT id FROM transacao_pontos WHERE cupom_resgatado_id = ?", Long.class,
                resgate.cupom().id());
        Map<String, Object> log = logUnico();
        assertLog(log, "CUPOM_RESGATADO", gestante.getId(), gestante.getId(), null, debito, resgate.cupom().id(), 60);
        assertEquals(hora(resgate(resgate.cupom().id()), "data_resgate"), hora(log, "data_hora"));
    }

    // O toque repetido com a mesma Idempotency-Key devolve o resgate feito e não grava outra linha
    @Test
    void resgateDeCupomRepetidoNaoGravaOutraLinha() {
        comSaldo(100);
        Cupom cupom = cupom(60);
        UUID chave = UUID.randomUUID();
        cupomService.resgatar(cupom.getId(), gestante.getId(), chave);

        assertTrue(cupomService.resgatar(cupom.getId(), gestante.getId(), chave).repetido());

        assertEquals(1, logs().size());
    }

    @Test
    void resgateDeCupomRecusadoNaoGravaLog() {
        comSaldo(10);
        Cupom caro = cupom(60);
        Cupom inativo = cupom(5);
        inativo.alterarAtivo(false);
        cupomRepository.save(inativo);

        assertThrows(ConflitoException.class, () -> cupomService.resgatar(caro.getId(), gestante.getId(), null));
        assertThrows(ConflitoException.class, () -> cupomService.resgatar(inativo.getId(), gestante.getId(), null));
        assertThrows(NaoEncontradoException.class, () -> cupomService.resgatar(-1L, gestante.getId(), null));

        assertEquals(List.of(), logs());
    }

    @Test
    void falhaNoLogDesfazOResgateEODebito() {
        comSaldo(100);
        Cupom cupom = cupom(60);
        logFalha();

        assertThrows(DataIntegrityViolationException.class,
                () -> cupomService.resgatar(cupom.getId(), gestante.getId(), UUID.randomUUID()));

        assertEquals(100, saldo());
        assertEquals(0, resgates());
        assertEquals(0, transacoes());
        assertEquals(List.of(), logs());
    }

    // ---- Promoção resgatada ----

    @Test
    void resgateDePromocaoGravaUmaLinhaSemPontos() {
        Promocao promocao = promocao();

        CupomService.Resgate resgate = promocaoService.resgatar(promocao.getId(), gestante.getId(), UUID.randomUUID());

        Map<String, Object> log = logUnico();
        assertLog(log, "PROMOCAO_RESGATADA", gestante.getId(), gestante.getId(), null, null, resgate.cupom().id(),
                null);
        assertEquals(hora(resgate(resgate.cupom().id()), "data_resgate"), hora(log, "data_hora"));
    }

    @Test
    void resgateDePromocaoRepetidoOuJaResgatadoNaoGravaOutraLinha() {
        Promocao promocao = promocao();
        UUID chave = UUID.randomUUID();
        promocaoService.resgatar(promocao.getId(), gestante.getId(), chave);

        // A mesma chave: o resgate feito; outra chave: 409 PROMOCAO_JA_RESGATADA
        assertTrue(promocaoService.resgatar(promocao.getId(), gestante.getId(), chave).repetido());
        var erro = assertThrows(ConflitoException.class,
                () -> promocaoService.resgatar(promocao.getId(), gestante.getId(), UUID.randomUUID()));
        assertEquals(CodigoErro.PROMOCAO_JA_RESGATADA, erro.getCodigo());

        assertEquals(1, logs().size());
    }

    @Test
    void falhaNoLogDesfazOResgateDaPromocao() {
        Promocao promocao = promocao();
        logFalha();

        assertThrows(DataIntegrityViolationException.class,
                () -> promocaoService.resgatar(promocao.getId(), gestante.getId(), UUID.randomUUID()));

        assertEquals(0, resgates());
        assertEquals(List.of(), logs());
    }

    // ---- Resgate validado ----

    private Long resgateDeCupom() {
        comSaldo(100);
        return cupomService.resgatar(cupom(60).getId(), gestante.getId(), null).cupom().id();
    }

    private String codigo(Long idResgate) {
        return jdbc.queryForObject("SELECT codigo FROM cupom_resgatado WHERE id = ?", String.class, idResgate);
    }

    private int validacoes(Long idResgate) {
        return jdbc.queryForObject("SELECT count(*) FROM log_auditoria WHERE cupom_resgatado_id = ?"
                + " AND acao = 'RESGATE_VALIDADO'", Integer.class, idResgate);
    }

    @Test
    void validacaoGravaUmaLinhaComAEmpresa() {
        Long id = resgateDeCupom();

        resgateService.validar(empresa.getId(), codigo(id));

        assertEquals(1, validacoes(id));
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM log_auditoria WHERE cupom_resgatado_id = ?"
                + " AND acao = 'RESGATE_VALIDADO'", id);
        assertLog(log, "RESGATE_VALIDADO", empresa.getId(), gestante.getId(), null, null, id, null);
        // A hora da própria operação: a mesma gravada como data de uso
        assertEquals(hora(resgate(id), "data_uso"), hora(log, "data_hora"));
    }

    @Test
    void validacaoRecusadaNaoGravaLog() {
        Long id = resgateDeCupom();
        resgateService.validar(empresa.getId(), codigo(id));
        EmpresaParceira outra = empresaRepository.save(new EmpresaParceira("Outra", "%014d".formatted(
                SEQUENCIA.incrementAndGet()), null, null, null, "emplog" + SEQUENCIA.incrementAndGet(), "hash"));

        // Já utilizado (409), de outra empresa (404) e inexistente (404)
        var erro = assertThrows(ConflitoException.class, () -> resgateService.validar(empresa.getId(), codigo(id)));
        assertEquals(CodigoErro.RESGATE_JA_UTILIZADO, erro.getCodigo());
        assertThrows(NaoEncontradoException.class, () -> resgateService.validar(outra.getId(), codigo(id)));
        assertThrows(NaoEncontradoException.class, () -> resgateService.validar(empresa.getId(), "ZZZZZZZZ"));

        // Só a validação que deu certo
        assertEquals(1, validacoes(id));
    }

    // O 409 do vencido comita o EXPIRADO (noRollbackFor), mas não é uma validação: não grava log
    @Test
    void validacaoDeVencidoGravaExpiradoSemLog() {
        Long id = resgateDeCupom();
        jdbc.update("UPDATE cupom_resgatado SET data_validade = ? WHERE id = ?", HOJE.minusDays(1), id);

        var erro = assertThrows(ConflitoException.class, () -> resgateService.validar(empresa.getId(), codigo(id)));

        assertEquals(CodigoErro.RESGATE_EXPIRADO, erro.getCodigo());
        assertEquals("EXPIRADO", resgate(id).get("status"));
        assertEquals(0, validacoes(id));
        // Só a linha do resgate
        assertEquals(List.of("CUPOM_RESGATADO"), logs().stream().map(l -> l.get("acao")).toList());
    }

    // A falha ao gravar o log não é ConflitoException: o noRollbackFor não a cobre, e a validação é desfeita
    @Test
    void falhaNoLogDesfazAValidacao() {
        Long id = resgateDeCupom();
        logFalha();

        assertThrows(DataIntegrityViolationException.class,
                () -> resgateService.validar(empresa.getId(), codigo(id)));

        assertEquals("ATIVO", resgate(id).get("status"));
        assertNull(resgate(id).get("data_uso"));
        assertEquals(0, validacoes(id));
    }

    // ---- Garantias do próprio log ----

    // O gatilho da V24 barra UPDATE e DELETE: o registro não pode ser alterado nem apagado
    @Test
    void gatilhoBarraUpdateEDeleteNoLog() {
        promocaoService.resgatar(promocao().getId(), gestante.getId(), null);
        Long id = id(logUnico(), "id");

        var update = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE log_auditoria SET pontos = 1 WHERE id = ?", id));
        assertTrue(update.getMostSpecificCause().getMessage().contains("log_auditoria não aceita UPDATE"),
                update.getMostSpecificCause().getMessage());
        var delete = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM log_auditoria WHERE id = ?", id));
        assertTrue(delete.getMostSpecificCause().getMessage().contains("log_auditoria não aceita DELETE"),
                delete.getMostSpecificCause().getMessage());

        Map<String, Object> intacto = logUnico();
        assertEquals(id, id(intacto, "id"));
        assertNull(intacto.get("pontos"));
    }

    // Sem a transação da operação, a Auditoria não grava sozinha (MANDATORY): o log nunca fica sem a operação
    @Test
    void auditoriaExigeATransacaoDaOperacao() {
        Agendamento exame = agendamentoDeHoje(30);

        assertThrows(IllegalTransactionStateException.class,
                () -> auditoria.agendamentosCriados(medico.getId(), List.of(exame)));

        assertEquals(List.of(), logs());
    }
}
