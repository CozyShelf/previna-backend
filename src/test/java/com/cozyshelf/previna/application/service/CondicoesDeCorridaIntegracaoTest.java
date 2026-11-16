package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.request.ConteudoRequest;
import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.dto.request.PromocaoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.dto.response.ConfirmacaoResponse;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.CategoriaConteudo;
import com.cozyshelf.previna.domain.model.ConteudoInformativo;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.TipoConteudo;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.AgendamentoRepository;
import com.cozyshelf.previna.domain.repository.ConteudoInformativoRepository;
import com.cozyshelf.previna.domain.repository.CupomRepository;
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
}
