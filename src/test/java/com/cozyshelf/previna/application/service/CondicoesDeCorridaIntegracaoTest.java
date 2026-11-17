package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AgendamentoRequest;
import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.request.ItemAgendamentoRequest;
import com.cozyshelf.previna.application.dto.request.ConteudoRequest;
import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.dto.response.AgendamentoResponse;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.ConteudoInformativo;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.ConteudoInformativoRepository;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.FotoGestanteRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.ImagemConteudoRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private CupomService cupomService;
    @Autowired
    private PromocaoService promocaoService;
    @Autowired
    private ConteudoService conteudoService;
    @Autowired
    private CupomRepository cupomRepository;
    @Autowired
    private ConteudoInformativoRepository conteudoRepository;
    @MockitoSpyBean
    private ProdutoRepository produtoRepository;
    @MockitoSpyBean
    private PromocaoRepository promocaoRepository;
    @MockitoSpyBean
    private ImagemConteudoRepository imagemConteudoRepository;
    @Autowired
    private EntityManager entityManager;
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
        reset(fotoRepository, usuarioRepository, produtoRepository, promocaoRepository, imagemConteudoRepository);
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

    // ---- Edições concorrentes do mesmo registro ----

    // Segura a primeira transação que chegar ao ponto marcado até o teste liberar
    private static final class Pausa {
        private final AtomicBoolean usada = new AtomicBoolean();
        private final CountDownLatch chegou = new CountDownLatch(1);
        private final CountDownLatch liberar = new CountDownLatch(1);

        void segurarAPrimeira() throws InterruptedException {
            if (usada.compareAndSet(false, true)) {
                chegou.countDown();
                assertTrue(liberar.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            }
        }
    }

    // A edição é segurada no meio, já com o registro lido; a outra alteração roda enquanto isso. Sem a trava, ela
    // comita no meio e a edição grava por cima o valor que leu; com a trava, ela espera a edição terminar
    private void alterarDuranteAEdicao(Pausa pausa, Callable<?> edicao, Callable<?> alteracao) throws Exception {
        Future<?> editando = executor.submit(edicao);
        assertTrue(pausa.chegou.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
        Future<?> alterando = executor.submit(alteracao);
        esperarAte(() -> alterando.isDone() || haTransacaoEsperandoTrava());
        pausa.liberar.countDown();
        editando.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        alterando.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
    }

    private EmpresaParceira empresa() {
        long n = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n), null, null, null,
                "empresa" + n, "hash"));
    }

    @Test
    void desativarOCupomDuranteUmaEdicaoNaoEDesfeitoPelaEdicao() throws Exception {
        EmpresaParceira empresa = empresa();
        Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null, Categoria.FRALDAS,
                null));
        LocalDate validade = LocalDate.now().plusDays(30);
        Cupom cupom = cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN, validade, 100));
        Pausa pausa = new Pausa();
        // A edição confere o produto depois de ler o cupom e antes de gravar
        doAnswer(chamada -> {
            pausa.segurarAPrimeira();
            return Optional.ofNullable(entityManager.find(Produto.class, chamada.getArgument(0)));
        }).when(produtoRepository).findByIdAndEmpresaId(any(), any());

        alterarDuranteAEdicao(pausa,
                () -> cupomService.atualizar(empresa.getId(), cupom.getId(),
                        new CupomRequest(fralda.getId(), new BigDecimal("15"), validade, 50)),
                () -> cupomService.alterarAtivo(empresa.getId(), cupom.getId(), false));

        var linha = jdbc.queryForMap("SELECT ativo, pontos_necessarios FROM cupom WHERE id = ?", cupom.getId());
        assertEquals(false, linha.get("ativo"));
        assertEquals(50, linha.get("pontos_necessarios"));
    }

    // Lê a promoção como o repositório leria (com ou sem trava) e segura a primeira transação logo depois
    private Answer<Optional<Promocao>> promocaoLidaComPausa(LockModeType trava, Pausa pausa) {
        return chamada -> {
            Optional<Promocao> promocao = entityManager.createQuery(
                            "select p from Promocao p where p.id = :id and p.empresaId = :empresaId", Promocao.class)
                    .setParameter("id", chamada.getArgument(0))
                    .setParameter("empresaId", chamada.getArgument(1))
                    .setLockMode(trava)
                    .getResultStream().findFirst();
            pausa.segurarAPrimeira();
            return promocao;
        };
    }

    @Test
    void desativarAPromocaoDuranteUmaEdicaoNaoEDesfeitoPelaEdicao() throws Exception {
        EmpresaParceira empresa = empresa();
        LocalDate inicio = LocalDate.now();
        Promocao promocao = promocaoRepository.save(new Promocao(empresa.getId(), "Semana do bebê", null,
                BigDecimal.TEN, inicio, inicio.plusDays(7), Categoria.FRALDAS));
        Pausa pausa = new Pausa();
        // A PromocaoService não chama nada entre ler e gravar: a pausa vai logo depois da leitura
        doAnswer(promocaoLidaComPausa(LockModeType.NONE, pausa))
                .when(promocaoRepository).findByIdAndEmpresaId(any(), any());
        doAnswer(promocaoLidaComPausa(LockModeType.PESSIMISTIC_WRITE, pausa))
                .when(promocaoRepository).findByIdAndEmpresaIdParaAtualizar(any(), any());

        alterarDuranteAEdicao(pausa,
                () -> promocaoService.atualizar(empresa.getId(), promocao.getId(), new PromocaoRequest(
                        "Semana do bebê 2", null, new BigDecimal("20"), inicio, inicio.plusDays(7), Categoria.FRALDAS)),
                () -> promocaoService.alterarAtivo(empresa.getId(), promocao.getId(), false));

        var linha = jdbc.queryForMap("SELECT ativo, titulo FROM promocao WHERE id = ?", promocao.getId());
        assertEquals(false, linha.get("ativo"));
        assertEquals("Semana do bebê 2", linha.get("titulo"));
    }

    @Test
    void publicarOConteudoDuranteUmaEdicaoNaoEDesfeitoPelaEdicao() throws Exception {
        ConteudoInformativo conteudo = conteudoRepository.save(new ConteudoInformativo(medico.getId(), "Hidratação",
                CategoriaConteudo.DICA, TipoConteudo.TEXTO, null, "Beba água", null));
        Pausa pausa = new Pausa();
        // Fora de FOTO, a edição apaga a imagem guardada depois de alterar o conteúdo e antes de gravar
        doAnswer(chamada -> {
            pausa.segurarAPrimeira();
            return null;
        }).when(imagemConteudoRepository).deleteAllById(any());

        alterarDuranteAEdicao(pausa,
                () -> conteudoService.atualizar(conteudo.getId(), new ConteudoRequest("Hidratação na gestação",
                        CategoriaConteudo.DICA, TipoConteudo.TEXTO, null, "Beba bastante água", null), null),
                () -> conteudoService.alterarPublicacao(conteudo.getId(), true));

        var linha = jdbc.queryForMap("SELECT publicado, publicado_em, titulo FROM conteudo_informativo WHERE id = ?",
                conteudo.getId());
        assertEquals(true, linha.get("publicado"));
        assertNotNull(linha.get("publicado_em"));
        assertEquals("Hidratação na gestação", linha.get("titulo"));
    }

    @Test
    void excluirOConteudoDuranteUmaEdicaoEsperaAEdicao() throws Exception {
        ConteudoInformativo conteudo = conteudoRepository.save(new ConteudoInformativo(medico.getId(), "Hidratação",
                CategoriaConteudo.DICA, TipoConteudo.TEXTO, null, "Beba água", null));
        Pausa pausa = new Pausa();
        doAnswer(chamada -> {
            pausa.segurarAPrimeira();
            return null;
        }).when(imagemConteudoRepository).deleteAllById(any());

        // Sem a trava, a exclusão comitava no meio e a edição falhava ao gravar uma linha que não existe mais
        alterarDuranteAEdicao(pausa,
                () -> conteudoService.atualizar(conteudo.getId(), new ConteudoRequest("Hidratação na gestação",
                        CategoriaConteudo.DICA, TipoConteudo.TEXTO, null, "Beba bastante água", null), null),
                () -> {
                    conteudoService.excluir(conteudo.getId());
                    return null;
                });

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM conteudo_informativo WHERE id = ?",
                Integer.class, conteudo.getId()));
    }

    // ---- Confirmação de agendamentos (já protegida: trava do agendamento e da gestante) ----

    private int creditos(Agendamento agendamento) {
        return jdbc.queryForObject("SELECT count(*) FROM transacao_pontos WHERE agendamento_id = ?", Integer.class,
                agendamento.getId());
    }

    @Test
    void confirmarERegistrarNaoComparecimentoAoMesmoTempoTemUmDesfechoSo() throws Exception {
        // Algumas rodadas: a ordem de quem pega a trava primeiro varia
        for (int rodada = 0; rodada < 5; rodada++) {
            Gestante gestante = gestante();
            Agendamento agendamento = agendamentoDeHoje(gestante, 40);

            List<Future<Object>> futuros = aoMesmoTempo(List.of(
                    () -> agendamentoService.confirmar(agendamento.getId(), medico.getId()),
                    () -> agendamentoService.registrarNaoComparecimento(agendamento.getId(), medico.getId())));
            String confirmacao = desfecho(futuros.get(0));
            String naoComparecimento = desfecho(futuros.get(1));

            assertTrue(confirmacao == null ^ naoComparecimento == null,
                    "esperava um desfecho só: " + confirmacao + " / " + naoComparecimento);
            assertEquals("Este agendamento já foi resolvido",
                    confirmacao == null ? naoComparecimento : confirmacao);
            String status = jdbc.queryForObject("SELECT status FROM agendamento WHERE id = ?", String.class,
                    agendamento.getId());
            if (confirmacao == null) {
                assertEquals("REALIZADO", status);
                assertEquals(1, creditos(agendamento));
                assertEquals(40, saldo(gestante));
            } else {
                // Não comparecimento com pontos creditados era o estrago de antes da trava
                assertEquals("NAO_COMPARECEU", status);
                assertEquals(0, creditos(agendamento));
                assertEquals(0, saldo(gestante));
            }
        }
    }

    @Test
    void duasConfirmacoesSimultaneasDaMesmaGestanteSomamAsDuas() throws Exception {
        Gestante gestante = gestante();
        Agendamento exame = agendamentoDeHoje(gestante, 30);
        Agendamento consulta = agendamentoDeHoje(gestante, 45);

        List<Future<ConfirmacaoResponse>> futuros = aoMesmoTempo(List.of(
                () -> agendamentoService.confirmar(exame.getId(), medico.getId()),
                () -> agendamentoService.confirmar(consulta.getId(), medico.getId())));
        int saldoDaPrimeira = futuros.get(0).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS).saldoPontos();
        int saldoDaSegunda = futuros.get(1).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS).saldoPontos();

        assertEquals(75, saldo(gestante));
        assertEquals(1, creditos(exame));
        assertEquals(1, creditos(consulta));
        // Cada resposta mostra o saldo depois do próprio crédito: a que comitou por último vê a soma
        assertEquals(75, Math.max(saldoDaPrimeira, saldoDaSegunda));
        assertTrue(Math.min(saldoDaPrimeira, saldoDaSegunda) == 30 || Math.min(saldoDaPrimeira, saldoDaSegunda) == 45);
    }

    // ---- Cadastro de agendamentos (índice parcial agendamento_pendente_unico, V21) ----

    private static final String PENDENTE_REPETIDO = "Essa gestante já tem esse exame ou consulta pendente nessa data";

    private TipoAtendimento tipo(int pontos) {
        return tipoRepository.save(new TipoAtendimento(medico.getId(), "Exame " + SEQUENCIA.incrementAndGet(),
                CategoriaAtendimento.EXAME, pontos));
    }

    private static AgendamentoRequest pedido(Gestante gestante, TipoAtendimento tipo, LocalDate... datas) {
        return new AgendamentoRequest(gestante.getId(), java.util.Arrays.stream(datas)
                .map(data -> new ItemAgendamentoRequest(tipo.getId(), data, null))
                .toList());
    }

    private int agendamentos(Gestante gestante, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM agendamento WHERE gestante_id = ? AND status = ?",
                Integer.class, gestante.getId(), status);
    }

    // Duplo clique, duas abas ou dois médicos: os dois leem "nada pendente" ao mesmo tempo. O segundo INSERT espera o
    // primeiro no índice e falha quando ele comita; antes, ficavam dois AGENDADO e a gestante ganhava os pontos duas
    // vezes. Algumas rodadas, porque a ordem de chegada varia
    @Test
    void doisCadastrosSimultaneosIguaisGravamUmPendenteSo() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            Gestante gestante = gestante();
            TipoAtendimento tipo = tipo(25);
            AgendamentoRequest dados = pedido(gestante, tipo, LocalDate.now().plusDays(3));

            List<Future<List<AgendamentoResponse>>> futuros = aoMesmoTempo(List.of(
                    () -> agendamentoService.agendar(dados, medico.getId()),
                    () -> agendamentoService.agendar(dados, medico.getId())));
            String primeiro = desfecho(futuros.get(0));
            String segundo = desfecho(futuros.get(1));

            assertTrue(primeiro == null ^ segundo == null, "esperava um sucesso só: " + primeiro + " / " + segundo);
            assertEquals(PENDENTE_REPETIDO, primeiro == null ? segundo : primeiro);
            assertEquals(1, agendamentos(gestante, "AGENDADO"));
        }
    }

    // O item repetido na mesma requisição cai no mesmo índice e a transação inteira volta atrás, inclusive o item
    // diferente que vinha antes
    @Test
    void itemRepetidoNaMesmaRequisicaoNaoGravaNada() {
        Gestante gestante = gestante();
        TipoAtendimento tipo = tipo(25);
        LocalDate data = LocalDate.now().plusDays(3);

        var erro = assertThrows(ConflitoException.class, () -> agendamentoService.agendar(
                pedido(gestante, tipo, data.plusDays(1), data, data), medico.getId()));

        assertEquals(PENDENTE_REPETIDO, erro.getMessage());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM agendamento WHERE gestante_id = ?", Integer.class,
                gestante.getId()));
    }

    @Test
    void mesmoTipoEmOutraDataContinuaPermitido() {
        Gestante gestante = gestante();
        TipoAtendimento tipo = tipo(25);
        LocalDate data = LocalDate.now().plusDays(3);

        agendamentoService.agendar(pedido(gestante, tipo, data), medico.getId());
        agendamentoService.agendar(pedido(gestante, tipo, data.plusDays(7)), medico.getId());

        assertEquals(2, agendamentos(gestante, "AGENDADO"));
    }

    @Test
    void mesmoTipoEDataDepoisDaRealizacaoContinuaPermitido() {
        Gestante gestante = gestante();
        TipoAtendimento tipo = tipo(25);
        LocalDate hoje = LocalDate.now();
        Long anterior = agendamentoService.agendar(pedido(gestante, tipo, hoje), medico.getId()).getFirst().id();
        agendamentoService.confirmar(anterior, medico.getId());

        agendamentoService.agendar(pedido(gestante, tipo, hoje), medico.getId());

        assertEquals(1, agendamentos(gestante, "REALIZADO"));
        assertEquals(1, agendamentos(gestante, "AGENDADO"));
    }

    @Test
    void mesmoTipoEDataDepoisDoNaoComparecimentoContinuaPermitido() {
        Gestante gestante = gestante();
        TipoAtendimento tipo = tipo(25);
        LocalDate hoje = LocalDate.now();
        Long anterior = agendamentoService.agendar(pedido(gestante, tipo, hoje), medico.getId()).getFirst().id();
        agendamentoService.registrarNaoComparecimento(anterior, medico.getId());

        agendamentoService.agendar(pedido(gestante, tipo, hoje), medico.getId());

        assertEquals(1, agendamentos(gestante, "NAO_COMPARECEU"));
        assertEquals(1, agendamentos(gestante, "AGENDADO"));
    }

    // ---- Promoção resgatável (V22: índice cupom_resgatado_promocao_uma_vez) ----

    private static final String JA_RESGATADA = "Promoção já resgatada";
    private static final String SOBREPOSTA = "Já existe promoção ativa dessa categoria nesse período";

    @Autowired
    private OfertaService ofertaService;

    private Gestante gestanteComSaldo(int pontos) {
        Gestante gestante = gestante();
        jdbc.update("UPDATE gestante SET saldo_pontos = ? WHERE usuario_id = ?", pontos, gestante.getId());
        return gestante;
    }

    private Promocao promocaoVigente(EmpresaParceira empresa, Categoria categoria) {
        return promocaoRepository.save(new Promocao(empresa.getId(), "Semana do bebê", null, new BigDecimal("15"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5), categoria));
    }

    private int resgatesDaPromocao(Gestante gestante, Promocao promocao) {
        return jdbc.queryForObject("SELECT count(*) FROM cupom_resgatado WHERE gestante_id = ? AND promocao_id = ?",
                Integer.class, gestante.getId(), promocao.getId());
    }

    private int transacoes(Gestante gestante) {
        return jdbc.queryForObject("SELECT count(*) FROM transacao_pontos WHERE gestante_id = ?", Integer.class,
                gestante.getId());
    }

    // Duplo toque com chaves diferentes (ou duas telas): só um resgate passa, e quem decide é o índice único.
    // Algumas rodadas, porque a ordem de chegada varia
    @Test
    void doisResgatesSimultaneosDaMesmaPromocaoComChavesDiferentesGravamUmSo() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            Gestante gestante = gestanteComSaldo(70);
            Promocao promocao = promocaoVigente(empresa(), Categoria.FRALDAS);

            List<Future<CupomService.Resgate>> futuros = aoMesmoTempo(List.of(
                    () -> promocaoService.resgatar(promocao.getId(), gestante.getId(), java.util.UUID.randomUUID()),
                    () -> promocaoService.resgatar(promocao.getId(), gestante.getId(), java.util.UUID.randomUUID())));
            String primeiro = desfecho(futuros.get(0));
            String segundo = desfecho(futuros.get(1));

            assertTrue(primeiro == null ^ segundo == null, "esperava um sucesso só: " + primeiro + " / " + segundo);
            assertEquals(JA_RESGATADA, primeiro == null ? segundo : primeiro);
            assertEquals(1, resgatesDaPromocao(gestante, promocao));
            // Promoção não custa pontos: saldo igual e nenhuma transação
            assertEquals(70, saldo(gestante));
            assertEquals(0, transacoes(gestante));
        }
    }

    @Test
    void doisResgatesSimultaneosComAMesmaChaveDevolvemOMesmoResgate() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            Gestante gestante = gestanteComSaldo(0);
            Promocao promocao = promocaoVigente(empresa(), Categoria.FRALDAS);
            java.util.UUID chave = java.util.UUID.randomUUID();

            List<Future<CupomService.Resgate>> futuros = aoMesmoTempo(List.of(
                    () -> promocaoService.resgatar(promocao.getId(), gestante.getId(), chave),
                    () -> promocaoService.resgatar(promocao.getId(), gestante.getId(), chave)));
            CupomService.Resgate primeiro = futuros.get(0).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            CupomService.Resgate segundo = futuros.get(1).get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);

            assertTrue(primeiro.repetido() ^ segundo.repetido(), "esperava um resgate novo e um repetido");
            assertEquals(primeiro.cupom().codigo(), segundo.cupom().codigo());
            assertEquals(1, resgatesDaPromocao(gestante, promocao));
        }
    }

    @Test
    void outraGestanteResgataAMesmaPromocao() {
        Promocao promocao = promocaoVigente(empresa(), Categoria.FRALDAS);
        Gestante maria = gestanteComSaldo(0);
        Gestante joana = gestanteComSaldo(0);

        promocaoService.resgatar(promocao.getId(), maria.getId(), null);
        promocaoService.resgatar(promocao.getId(), joana.getId(), null);

        assertEquals(1, resgatesDaPromocao(maria, promocao));
        assertEquals(1, resgatesDaPromocao(joana, promocao));
    }

    @Test
    void aMesmaGestanteResgataPromocoesDiferentesMasCadaUmaUmaVez() {
        EmpresaParceira empresa = empresa();
        Promocao fraldas = promocaoVigente(empresa, Categoria.FRALDAS);
        Promocao roupas = promocaoVigente(empresa, Categoria.ROUPAS);
        Gestante gestante = gestanteComSaldo(0);

        promocaoService.resgatar(fraldas.getId(), gestante.getId(), null);
        promocaoService.resgatar(roupas.getId(), gestante.getId(), null);
        var erro = assertThrows(ConflitoException.class,
                () -> promocaoService.resgatar(fraldas.getId(), gestante.getId(), null));

        assertEquals(JA_RESGATADA, erro.getMessage());
        assertEquals(1, resgatesDaPromocao(gestante, fraldas));
        assertEquals(1, resgatesDaPromocao(gestante, roupas));
    }

    // Cupom e promoção do mesmo produto são resgates independentes; o cupom continua N vezes (promocao_id nulo não
    // colide no índice)
    @Test
    void cupomDoMesmoProdutoContinuaResgatavelVariasVezes() {
        EmpresaParceira empresa = empresa();
        Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null, Categoria.FRALDAS,
                null));
        Cupom cupom = cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN,
                LocalDate.now().plusDays(5), 30));
        Promocao promocao = promocaoVigente(empresa, Categoria.FRALDAS);
        Gestante gestante = gestanteComSaldo(100);

        var resgatePromocao = promocaoService.resgatar(promocao.getId(), gestante.getId(), null).cupom();
        cupomService.resgatar(cupom.getId(), gestante.getId(), null);
        var resgateCupom = cupomService.resgatar(cupom.getId(), gestante.getId(), null).cupom();

        assertEquals(com.cozyshelf.previna.domain.model.TipoResgate.PROMOCAO, resgatePromocao.tipo());
        assertEquals(com.cozyshelf.previna.domain.model.TipoResgate.CUPOM, resgateCupom.tipo());
        assertEquals(40, saldo(gestante));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM cupom_resgatado WHERE gestante_id = ? AND cupom_id = ?",
                Integer.class, gestante.getId(), cupom.getId()));
        assertEquals(1, resgatesDaPromocao(gestante, promocao));
        assertEquals(2, transacoes(gestante));
        // O extrato só tem os débitos dos cupons
        var extrato = jdbc.queryForList("SELECT cupom_resgatado_id FROM transacao_pontos WHERE gestante_id = ?",
                Long.class, gestante.getId());
        assertFalse(extrato.contains(resgatePromocao.id()));
    }

    @Test
    void detalheDoProdutoMarcaAPromocaoResgatadaSoParaQuemResgatou() {
        EmpresaParceira empresa = empresa();
        Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null, Categoria.FRALDAS,
                null));
        Promocao promocao = promocaoVigente(empresa, Categoria.FRALDAS);
        Gestante maria = gestanteComSaldo(0);
        Gestante joana = gestanteComSaldo(0);
        promocaoService.resgatar(promocao.getId(), maria.getId(), null);

        assertTrue(ofertaService.detalhar(maria.getId(), fralda.getId()).promocoes().getFirst().resgatada());
        assertFalse(ofertaService.detalhar(joana.getId(), fralda.getId()).promocoes().getFirst().resgatada());
    }

    // ---- Uma promoção ativa por categoria em cada período (V23: exclusão promocao_sem_sobreposicao) ----

    private static PromocaoRequest periodo(Categoria categoria, LocalDate inicio, LocalDate fim) {
        return new PromocaoRequest("Semana do bebê", null, new BigDecimal("15"), inicio, fim, categoria);
    }

    private int ativas(EmpresaParceira empresa) {
        return jdbc.queryForObject("SELECT count(*) FROM promocao WHERE empresa_id = ? AND ativo", Integer.class,
                empresa.getId());
    }

    // Os dois leem "nenhuma sobreposta" ao mesmo tempo; o segundo INSERT espera o primeiro na exclusão e falha
    // quando ele comita
    @Test
    void doisCadastrosSimultaneosSobrepostosGravamUmSo() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            EmpresaParceira empresa = empresa();
            LocalDate hoje = LocalDate.now();

            List<Future<Object>> futuros = aoMesmoTempo(List.of(
                    () -> promocaoService.cadastrar(empresa.getId(), periodo(Categoria.FRALDAS, hoje, hoje.plusDays(7))),
                    () -> promocaoService.cadastrar(empresa.getId(),
                            periodo(Categoria.FRALDAS, hoje.plusDays(3), hoje.plusDays(10)))));
            String primeiro = desfecho(futuros.get(0));
            String segundo = desfecho(futuros.get(1));

            assertTrue(primeiro == null ^ segundo == null, "esperava um sucesso só: " + primeiro + " / " + segundo);
            assertEquals(SOBREPOSTA, primeiro == null ? segundo : primeiro);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM promocao WHERE empresa_id = ?", Integer.class,
                    empresa.getId()));
        }
    }

    // Duas promoções sobrepostas, as duas desativadas; reativar as duas ao mesmo tempo deixa uma ativa só
    @Test
    void duasReativacoesSimultaneasQueConflitamDeixamUmaAtiva() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            EmpresaParceira empresa = empresa();
            LocalDate hoje = LocalDate.now();
            Long primeira = promocaoService.cadastrar(empresa.getId(),
                    periodo(Categoria.ROUPAS, hoje, hoje.plusDays(7))).id();
            promocaoService.alterarAtivo(empresa.getId(), primeira, false);
            Long segunda = promocaoService.cadastrar(empresa.getId(),
                    periodo(Categoria.ROUPAS, hoje.plusDays(7), hoje.plusDays(14))).id();
            promocaoService.alterarAtivo(empresa.getId(), segunda, false);

            List<Future<Object>> futuros = aoMesmoTempo(List.of(
                    () -> promocaoService.alterarAtivo(empresa.getId(), primeira, true),
                    () -> promocaoService.alterarAtivo(empresa.getId(), segunda, true)));
            String a = desfecho(futuros.get(0));
            String b = desfecho(futuros.get(1));

            assertTrue(a == null ^ b == null, "esperava uma reativação só: " + a + " / " + b);
            assertEquals(SOBREPOSTA, a == null ? b : a);
            assertEquals(1, ativas(empresa));
        }
    }

    @Test
    void sobrepostasEmCategoriasOuEmpresasDiferentesSaoPermitidas() {
        EmpresaParceira loja = empresa();
        EmpresaParceira outra = empresa();
        LocalDate hoje = LocalDate.now();

        promocaoService.cadastrar(loja.getId(), periodo(Categoria.FRALDAS, hoje, hoje.plusDays(7)));
        promocaoService.cadastrar(loja.getId(), periodo(Categoria.ROUPAS, hoje, hoje.plusDays(7)));
        promocaoService.cadastrar(outra.getId(), periodo(Categoria.FRALDAS, hoje, hoje.plusDays(7)));

        assertEquals(2, ativas(loja));
        assertEquals(1, ativas(outra));
    }

    @Test
    void sobrepostaDesativadaNaoBloqueiaUmaNovaEDesativarNuncaConflita() {
        EmpresaParceira empresa = empresa();
        LocalDate hoje = LocalDate.now();
        Long antiga = promocaoService.cadastrar(empresa.getId(), periodo(Categoria.FRALDAS, hoje, hoje.plusDays(7))).id();
        promocaoService.alterarAtivo(empresa.getId(), antiga, false);

        Long nova = promocaoService.cadastrar(empresa.getId(), periodo(Categoria.FRALDAS, hoje, hoje.plusDays(7))).id();
        // Reativar a antiga agora conflita com a nova; desativar de novo não
        var erro = assertThrows(ConflitoException.class,
                () -> promocaoService.alterarAtivo(empresa.getId(), antiga, true));
        promocaoService.alterarAtivo(empresa.getId(), antiga, false);

        assertEquals(SOBREPOSTA, erro.getMessage());
        assertEquals(1, ativas(empresa));
        assertTrue(jdbc.queryForObject("SELECT ativo FROM promocao WHERE id = ?", Boolean.class, nova));
    }

    // Os dois dias são inclusos: terminar no dia 10 e começar no 11 não sobrepõe; começar no 10 sobrepõe
    @Test
    void periodosEncostadosNaoSobrepoemMasODiaDoFimConta() {
        EmpresaParceira empresa = empresa();
        LocalDate dia10 = LocalDate.now().plusDays(10);

        promocaoService.cadastrar(empresa.getId(), periodo(Categoria.REMEDIOS, dia10.minusDays(5), dia10));
        promocaoService.cadastrar(empresa.getId(), periodo(Categoria.REMEDIOS, dia10.plusDays(1), dia10.plusDays(5)));
        var erro = assertThrows(ConflitoException.class, () -> promocaoService.cadastrar(empresa.getId(),
                periodo(Categoria.REMEDIOS, dia10, dia10)));

        assertEquals(SOBREPOSTA, erro.getMessage());
        assertEquals(2, ativas(empresa));
    }

    @Test
    void editarParaSobreporOutraAtivaResponde409ENaoGrava() {
        EmpresaParceira empresa = empresa();
        LocalDate hoje = LocalDate.now();
        promocaoService.cadastrar(empresa.getId(), periodo(Categoria.FRALDAS, hoje, hoje.plusDays(7)));
        Long outra = promocaoService.cadastrar(empresa.getId(),
                periodo(Categoria.ROUPAS, hoje, hoje.plusDays(7))).id();

        var erro = assertThrows(ConflitoException.class, () -> promocaoService.atualizar(empresa.getId(), outra,
                periodo(Categoria.FRALDAS, hoje.plusDays(2), hoje.plusDays(3))));

        assertEquals(SOBREPOSTA, erro.getMessage());
        assertEquals("ROUPAS", jdbc.queryForObject("SELECT categoria FROM promocao WHERE id = ?", String.class, outra));
    }

    // ---- Meus cupons: expiração na leitura ----

    @Autowired
    private ResgateService resgateService;
    @Autowired
    private CupomResgatadoRepository cupomResgatadoRepository;
    @Autowired
    private GeradorCodigoResgate geradorCodigo;

    // Resgate de cupom da gestante, válido até a data indicada, já com o status indicado
    private CupomResgatado resgateValidoAte(Gestante gestante, Cupom cupom, EmpresaParceira empresa,
                                            LocalDate validade, StatusCupomResgatado status) {
        CupomResgatado resgate = cupomResgatadoRepository.save(new CupomResgatado(cupom, empresa, gestante,
                geradorCodigo.gerarCodigoUnico(), null));
        jdbc.update("UPDATE cupom_resgatado SET data_validade = ?, status = ? WHERE id = ?", validade, status.name(),
                resgate.getId());
        return resgate;
    }

    private String statusDoResgate(CupomResgatado resgate) {
        return jdbc.queryForObject("SELECT status FROM cupom_resgatado WHERE id = ?", String.class, resgate.getId());
    }

    // Duas listagens da mesma gestante ao mesmo tempo (o app aberto em dois aparelhos, ou o pull-to-refresh junto com
    // a abertura da tela), as duas com vencidos a expirar: o UPDATE condicionado a ATIVO faz a segunda esperar a trava
    // de linha da primeira e reavaliar o WHERE, que já não casa. Nenhuma falha, nenhum deadlock, e o UTILIZADO vencido
    // continua UTILIZADO. Algumas rodadas, porque a ordem de chegada varia
    @Test
    void duasListagensSimultaneasExpiramOsVencidosSemErroNemDeadlock() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            Gestante gestante = gestante();
            EmpresaParceira empresa = empresa();
            Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null,
                    Categoria.FRALDAS, null));
            Cupom cupom = cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN,
                    LocalDate.now().plusDays(5), 30));
            List<CupomResgatado> vencidos = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                vencidos.add(resgateValidoAte(gestante, cupom, empresa, LocalDate.now().minusDays(1 + i),
                        StatusCupomResgatado.ATIVO));
            }
            CupomResgatado utilizadoVencido = resgateValidoAte(gestante, cupom, empresa,
                    LocalDate.now().minusDays(3), StatusCupomResgatado.UTILIZADO);
            CupomResgatado valido = resgateValidoAte(gestante, cupom, empresa, LocalDate.now(),
                    StatusCupomResgatado.ATIVO);
            ResgateRequest semFiltro = new ResgateRequest(null, null, null, null, null, null);

            List<Future<PaginaResponse<ResgateResponse>>> futuros = aoMesmoTempo(List.of(
                    () -> resgateService.listar(gestante.getId(), semFiltro),
                    () -> resgateService.listar(gestante.getId(), semFiltro)));

            for (Future<PaginaResponse<ResgateResponse>> futuro : futuros) {
                // Sem exceção (deadlock ou outra) e já com o status novo: a segunda leitura vê o que a primeira gravou
                PaginaResponse<ResgateResponse> pagina = futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
                assertEquals(10, pagina.total());
                assertEquals(valido.getId(), pagina.itens().getFirst().id());
                assertEquals(1, pagina.itens().stream()
                        .filter(item -> item.status() == StatusCupomResgatado.ATIVO).count());
                assertEquals(1, pagina.itens().stream()
                        .filter(item -> item.status() == StatusCupomResgatado.UTILIZADO).count());
            }
            for (CupomResgatado vencido : vencidos) {
                assertEquals("EXPIRADO", statusDoResgate(vencido));
            }
            assertEquals("UTILIZADO", statusDoResgate(utilizadoVencido));
            assertEquals("ATIVO", statusDoResgate(valido));
        }
    }

    // A validação na loja (card #11) também será um UPDATE condicionado a ATIVO: quem chega primeiro decide, e o outro
    // não muda nada. Aqui a validação é simulada pelo próprio UPDATE, sobre um resgate ainda válido, durante as
    // listagens: ela nunca é desfeita, porque a expiração só pega ATIVO vencido
    @Test
    void validacaoDuranteAsListagensNaoEDesfeita() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            Gestante gestante = gestante();
            EmpresaParceira empresa = empresa();
            Produto fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null,
                    Categoria.FRALDAS, null));
            Cupom cupom = cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN,
                    LocalDate.now().plusDays(5), 30));
            for (int i = 0; i < 5; i++) {
                resgateValidoAte(gestante, cupom, empresa, LocalDate.now().minusDays(1), StatusCupomResgatado.ATIVO);
            }
            CupomResgatado validado = resgateValidoAte(gestante, cupom, empresa, LocalDate.now(),
                    StatusCupomResgatado.ATIVO);
            ResgateRequest semFiltro = new ResgateRequest(null, null, null, null, null, null);

            List<Future<Object>> futuros = aoMesmoTempo(List.of(
                    () -> resgateService.listar(gestante.getId(), semFiltro),
                    () -> jdbc.update("UPDATE cupom_resgatado SET status = 'UTILIZADO', data_uso = now()"
                            + " WHERE id = ? AND status = 'ATIVO'", validado.getId()),
                    () -> resgateService.listar(gestante.getId(), semFiltro)));
            for (Future<Object> futuro : futuros) {
                futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            }

            assertEquals("UTILIZADO", statusDoResgate(validado));
            assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM cupom_resgatado WHERE gestante_id = ?"
                    + " AND status = 'EXPIRADO'", Integer.class, gestante.getId()));
        }
    }
}
