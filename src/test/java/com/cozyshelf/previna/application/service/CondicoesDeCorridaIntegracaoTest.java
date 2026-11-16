package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.FotoGestanteRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    @MockitoSpyBean
    private FotoGestanteRepository fotoRepository;
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
        reset(fotoRepository);
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
}
