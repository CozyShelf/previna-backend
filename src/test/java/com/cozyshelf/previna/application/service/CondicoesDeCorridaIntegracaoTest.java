package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.FotoGestanteRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
import static org.mockito.Mockito.reset;

// Requisições concorrentes sobre a mesma linha, contra um PostgreSQL de verdade: travas e gravações atômicas só se
// provam no banco. Onde a disputa dependeria de sorte, um spy segura uma das transações no meio enquanto a outra
// comita. Roda só com PREVINA_TEST_DB_URL (ver ResgateCupomIntegracaoTest e o README).
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class CondicoesDeCorridaIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes
    private static final AtomicLong SEQUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L + 500_000_000L);
    private static final int ESPERA_SEGUNDOS = 15;

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private GestanteService gestanteService;
    @Autowired
    private AgendamentoService agendamentoService;
    @Autowired
    private EmpresaService empresaService;
    @MockitoSpyBean
    private FotoGestanteRepository fotoRepository;
    @MockitoSpyBean
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EmpresaParceiraRepository empresaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MedicoRepository medicoRepository;
    @Autowired
    private GestanteRepository gestanteRepository;
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
        reset(fotoRepository, usuarioRepository);
        executor = Executors.newFixedThreadPool(4);
        long n = SEQUENCIA.incrementAndGet();
        medico = medicoRepository.save(new Medico("Dra. Ana", cpf(n), null, "CRM-" + n, "medico" + n, "hash", false));
    }

    @AfterEach
    void encerrar() {
        executor.shutdownNow();
    }

    private static String cpf(long n) {
        return "%011d".formatted(n);
    }

    private Gestante gestante() {
        long n = SEQUENCIA.incrementAndGet();
        return gestanteRepository.save(new Gestante("Maria", cpf(n), null, "gestante" + n, "hash",
                medico, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()));
    }

    // Agendamento que já pode ser confirmado (a data prevista é hoje), valendo os pontos indicados
    private Agendamento agendamentoDeHoje(Gestante gestante, int pontos) {
        TipoAtendimento tipo = tipoRepository.save(new TipoAtendimento(medico.getId(),
                "Exame " + SEQUENCIA.incrementAndGet(), CategoriaAtendimento.EXAME, pontos));
        return agendamentoRepository.save(new Agendamento(gestante, tipo, medico, LocalDate.now(), null));
    }

    // Alguma transação parada esperando uma trava de linha (SELECT ... FOR UPDATE ou UPDATE) neste banco
    private boolean haTransacaoEsperandoTrava() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity"
                + " WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class) > 0;
    }

    private static void esperarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(ESPERA_SEGUNDOS);
        while (!condicao.getAsBoolean()) {
            assertTrue(System.nanoTime() < limite, "a condição esperada não aconteceu");
            Thread.sleep(20);
        }
    }

    // Dispara as tarefas ao mesmo tempo
    private <T> List<Future<T>> aoMesmoTempo(List<Callable<T>> tarefas) {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (Callable<T> tarefa : tarefas) {
            futuros.add(executor.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }
        largada.countDown();
        return futuros;
    }

    // Resultado de uma tarefa: null se deu certo, ou a mensagem da ConflitoException
    private static String desfecho(Future<?> futuro) throws Exception {
        try {
            futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException erro) {
            if (erro.getCause() instanceof ConflitoException conflito) {
                return conflito.getMessage();
            }
            throw erro;
        }
    }

    // Segura quem chegar à conferência do login (existsByLogin, depois da leitura da conta e antes de gravar) até a
    // outra requisição chegar também ou ficar parada na trava; sem a trava, as duas passavam da conferência
    // "primeiro acesso pendente"
    private void segurarNaConferenciaDoLogin(AtomicInteger chegadas, CountDownLatch liberar) {
        doAnswer(chamada -> {
            chegadas.incrementAndGet();
            assertTrue(liberar.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            // O spy de um repositório do Spring Data não tem método real a chamar
            return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM usuario WHERE login = ?)", Boolean.class,
                    chamada.<String>getArgument(0));
        }).when(usuarioRepository).existsByLogin(any());
    }

    private Map<String, Object> credenciais(Long idUsuario) {
        return jdbc.queryForMap("SELECT login, senha_hash, status_conta FROM usuario WHERE id = ?", idUsuario);
    }

    private int saldo(Gestante gestante) {
        return jdbc.queryForObject("SELECT saldo_pontos FROM gestante WHERE usuario_id = ?", Integer.class,
                gestante.getId());
    }

    // ---- Saldo de pontos ----

    @Test
    void editarOPerfilDuranteUmaConfirmacaoNaoApagaOCredito() throws Exception {
        Gestante gestante = gestante();
        Agendamento agendamento = agendamentoDeHoje(gestante, 50);
        CountDownLatch perfilAlterado = new CountDownLatch(1);
        CountDownLatch liberarPerfil = new CountDownLatch(1);
        // existsById roda no fim do atualizarPerfil: a gestante já foi lida (saldo 0) e alterada, mas não gravada
        doAnswer(chamada -> {
            perfilAlterado.countDown();
            assertTrue(liberarPerfil.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            // O spy de um repositório do Spring Data não tem método real a chamar; esta gestante não tem foto
            return false;
        }).when(fotoRepository).existsById(any());

        Future<?> perfil = executor.submit(() -> gestanteService.atualizarPerfil(gestante.getId(),
                new AtualizacaoPerfilGestanteRequest("11988887777", null, null), null));
        assertTrue(perfilAlterado.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
        // A confirmação credita e comita enquanto a edição do perfil está parada no meio
        ConfirmacaoResponse confirmacao = agendamentoService.confirmar(agendamento.getId(), medico.getId());
        liberarPerfil.countDown();
        perfil.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);

        assertEquals(50, confirmacao.saldoPontos());
        // Antes, a edição gravava o saldo 0 que tinha lido por cima do crédito
        assertEquals(50, saldo(gestante));
        assertEquals("11988887777", jdbc.queryForObject("SELECT telefone FROM gestante WHERE usuario_id = ?",
                String.class, gestante.getId()));
    }

    @Test
    void saldoNegativoEBarradoPeloBanco() {
        Gestante gestante = gestante();

        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE gestante SET saldo_pontos = -1 WHERE usuario_id = ?", gestante.getId()));
        assertEquals(0, saldo(gestante));
    }

    // ---- Primeiro acesso ----

    @Test
    void doisPrimeirosAcessosSimultaneosDaGestanteConcluemUmSo() throws Exception {
        Gestante gestante = gestante();
        long n = SEQUENCIA.incrementAndGet();
        AtomicInteger chegadas = new AtomicInteger();
        CountDownLatch liberar = new CountDownLatch(1);
        segurarNaConferenciaDoLogin(chegadas, liberar);

        List<Future<Object>> futuros = aoMesmoTempo(List.of(
                () -> { gestanteService.concluirPrimeiroAcesso(gestante.getId(),
                        primeiroAcessoGestante("maria.a" + n, "senhaDaA1"), null); return null; },
                () -> { gestanteService.concluirPrimeiroAcesso(gestante.getId(),
                        primeiroAcessoGestante("maria.b" + n, "senhaDaB2"), null); return null; }));
        esperarAte(() -> chegadas.get() == 2 || chegadas.get() == 1 && haTransacaoEsperandoTrava());
        liberar.countDown();

        verificarUmPrimeiroAcessoSo(gestante.getId(), futuros, "maria.a" + n, "senhaDaA1", "maria.b" + n,
                "senhaDaB2");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM dados_socioeconomicos WHERE gestante_id = ?",
                Integer.class, gestante.getId()));
    }

    @Test
    void doisPrimeirosAcessosSimultaneosDaEmpresaConcluemUmSo() throws Exception {
        long n = SEQUENCIA.incrementAndGet();
        EmpresaParceira empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n),
                null, null, null, "%014d".formatted(n), "hash"));
        AtomicInteger chegadas = new AtomicInteger();
        CountDownLatch liberar = new CountDownLatch(1);
        segurarNaConferenciaDoLogin(chegadas, liberar);

        List<Future<Object>> futuros = aoMesmoTempo(List.of(
                () -> { empresaService.concluirPrimeiroAcesso(empresa.getId(),
                        primeiroAcessoEmpresa("farmacia.a" + n, "senhaDaA1"), null); return null; },
                () -> { empresaService.concluirPrimeiroAcesso(empresa.getId(),
                        primeiroAcessoEmpresa("farmacia.b" + n, "senhaDaB2"), null); return null; }));
        esperarAte(() -> chegadas.get() == 2 || chegadas.get() == 1 && haTransacaoEsperandoTrava());
        liberar.countDown();

        verificarUmPrimeiroAcessoSo(empresa.getId(), futuros, "farmacia.a" + n, "senhaDaA1", "farmacia.b" + n,
                "senhaDaB2");
    }

    private static PrimeiroAcessoRequest primeiroAcessoGestante(String login, String senha) {
        return new PrimeiroAcessoRequest(login, senha, true, null, null, null, null);
    }

    private static PrimeiroAcessoEmpresaRequest primeiroAcessoEmpresa(String login, String senha) {
        return new PrimeiroAcessoEmpresaRequest(login, senha, "Rua A, 1", "Farmácia", null);
    }

    // Uma conclui, a outra recebe "Primeiro acesso já concluído", e ficam as credenciais da que concluiu
    private void verificarUmPrimeiroAcessoSo(Long idUsuario, List<Future<Object>> futuros, String loginA,
                                             String senhaA, String loginB, String senhaB) throws Exception {
        String primeira = desfecho(futuros.get(0));
        String segunda = desfecho(futuros.get(1));
        assertTrue(primeira == null ^ segunda == null, "esperava um sucesso só: " + primeira + " / " + segunda);
        assertEquals("Primeiro acesso já concluído", primeira == null ? segunda : primeira);

        boolean venceuA = primeira == null;
        var linha = credenciais(idUsuario);
        assertEquals(venceuA ? loginA : loginB, linha.get("login"));
        assertTrue(passwordEncoder.matches(venceuA ? senhaA : senhaB, (String) linha.get("senha_hash")));
        assertEquals("ATIVA", linha.get("status_conta"));
    }
}
