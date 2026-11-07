package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ExtratoRequest;
import com.cozyshelf.previna.application.dto.response.CupomDisponivelResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.TipoTransacao;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

// Contra um PostgreSQL de verdade: a trava (SELECT ... FOR UPDATE), o rollback, a consulta por validade e o
// carregamento do extrato só se provam no banco. Roda só com PREVINA_TEST_DB_URL apontando para um banco
// descartável (as migrações são aplicadas nele); sem a variável, o teste é pulado. Ver README.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class ResgateCupomIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes
    private static final AtomicLong SEQUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private CupomService cupomService;
    @MockitoSpyBean
    private PontuacaoService pontuacaoService;
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
    private JdbcTemplate jdbc;

    private Medico medico;
    private EmpresaParceira empresa;
    private Produto fralda;

    @BeforeEach
    void preparar() {
        reset(pontuacaoService);
        long n = SEQUENCIA.incrementAndGet();
        medico = medicoRepository.save(new Medico("Dra. Ana", cpf(n), null, "CRM-" + n, "medico" + n, "hash", false));
        empresa = empresaRepository.save(new EmpresaParceira("Farmácia " + n, "%014d".formatted(n), null, null,
                null, "empresa" + n, "hash"));
        fralda = produtoRepository.save(new Produto(empresa.getId(), "Fralda RN", null, Categoria.FRALDAS,
                new BigDecimal("39.90")));
    }

    private static String cpf(long n) {
        return "%011d".formatted(n);
    }

    private Gestante gestanteComSaldo(int saldo) {
        long n = SEQUENCIA.incrementAndGet();
        Gestante gestante = gestanteRepository.save(new Gestante("Maria", cpf(n), null, "gestante" + n, "hash",
                medico, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now()));
        jdbc.update("UPDATE gestante SET saldo_pontos = ? WHERE usuario_id = ?", saldo, gestante.getId());
        return gestante;
    }

    private Cupom cupom(int pontos, LocalDate validade) {
        return cupomRepository.save(new Cupom(empresa.getId(), fralda, BigDecimal.TEN, validade, pontos));
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
        return jdbc.queryForObject(
                "SELECT count(*) FROM transacao_pontos WHERE gestante_id = ? AND tipo = 'DEBITO'"
                        + " AND cupom_resgatado_id IS NOT NULL", Integer.class, gestante.getId());
    }

    // Dispara os resgates ao mesmo tempo e devolve quantos deram certo
    private int resgatarAoMesmoTempo(int vezes, Long idCupom, Long idGestante) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(vezes);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < vezes; i++) {
                resultados.add(executor.submit((Callable<Boolean>) () -> {
                    largada.await();
                    try {
                        cupomService.resgatar(idCupom, idGestante);
                        return true;
                    } catch (ConflitoException pontosInsuficientes) {
                        assertEquals("Pontos insuficientes", pontosInsuficientes.getMessage());
                        return false;
                    }
                }));
            }
            largada.countDown();
            int sucessos = 0;
            for (Future<Boolean> resultado : resultados) {
                sucessos += resultado.get() ? 1 : 0;
            }
            return sucessos;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resgatesSimultaneosNaoPassamDoSaldo() throws Exception {
        // Saldo para um resgate só: sem a trava, os dois leriam 100 e os dois passariam
        Gestante gestante = gestanteComSaldo(100);
        Cupom cupom = cupom(60, LocalDate.now().plusDays(5));

        assertEquals(1, resgatarAoMesmoTempo(2, cupom.getId(), gestante.getId()));
        assertEquals(40, saldo(gestante));
        assertEquals(1, resgates(gestante));
        assertEquals(1, debitos(gestante));
    }

    @Test
    void muitosResgatesSimultaneosNaoPerdemDebito() throws Exception {
        // Saldo para 5 de 10: sem a trava, um débito sobrescreveria o outro (saldo errado) ou o saldo ficaria negativo
        Gestante gestante = gestanteComSaldo(100);
        Cupom cupom = cupom(20, LocalDate.now().plusDays(5));

        assertEquals(5, resgatarAoMesmoTempo(10, cupom.getId(), gestante.getId()));
        assertEquals(0, saldo(gestante));
        assertEquals(5, resgates(gestante));
        assertEquals(5, debitos(gestante));
    }

    @Test
    void falhaNoDebitoDesfazOCupomResgatado() {
        Gestante gestante = gestanteComSaldo(100);
        Cupom cupom = cupom(60, LocalDate.now().plusDays(5));
        doThrow(new IllegalStateException("falha simulada"))
                .when(pontuacaoService).debitarPontos(any(), anyInt(), any());

        assertThrows(IllegalStateException.class, () -> cupomService.resgatar(cupom.getId(), gestante.getId()));
        assertEquals(100, saldo(gestante));
        assertEquals(0, resgates(gestante));
        assertEquals(0, debitos(gestante));
    }

    @Test
    void resgateGravaCodigoUnicoStatusAtivoEODebitoLigadoAoCupom() {
        Gestante gestante = gestanteComSaldo(100);
        Cupom cupom = cupom(60, LocalDate.now().plusDays(5));

        var resposta = cupomService.resgatar(cupom.getId(), gestante.getId());

        assertEquals(40, resposta.saldo());
        assertEquals(empresa.getRazaoSocial(), resposta.empresa());
        var linha = jdbc.queryForMap("SELECT codigo, status, data_resgate FROM cupom_resgatado WHERE id = ?",
                resposta.id());
        assertEquals(resposta.codigo(), linha.get("codigo"));
        assertEquals("ATIVO", linha.get("status"));
        assertNotNull(linha.get("data_resgate"));
        assertEquals(60, jdbc.queryForObject(
                "SELECT pontos FROM transacao_pontos WHERE cupom_resgatado_id = ?", Integer.class, resposta.id()));
    }

    @Test
    void codigoRepetidoEBarradoPeloBanco() {
        Gestante gestante = gestanteComSaldo(100);
        Cupom cupom = cupom(10, LocalDate.now().plusDays(5));
        var primeiro = cupomService.resgatar(cupom.getId(), gestante.getId());

        assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO cupom_resgatado (cupom_id, gestante_id, codigo, status, data_resgate)"
                        + " VALUES (?, ?, ?, 'ATIVO', now())", cupom.getId(), gestante.getId(), primeiro.codigo()));
    }

    @Test
    void extratoDescreveODebitoComOCupomForaDaTransacao() {
        // open-in-view está desligado: se o EntityGraph não trouxer o cupom e o produto, a descrição estoura
        // LazyInitializationException ao montar a resposta
        Gestante gestante = gestanteComSaldo(100);
        Cupom cupom = cupom(60, LocalDate.now().plusDays(5));
        cupomService.resgatar(cupom.getId(), gestante.getId());

        var extrato = pontuacaoService.gerarExtrato(gestante.getId(), new ExtratoRequest(TipoTransacao.DEBITO, null, null));

        assertEquals(60, extrato.totalGasto());
        assertEquals("Cupom: 10% de desconto em Fralda RN", extrato.historico().getFirst().descricao());
    }

    @Test
    void listaSoAtivosDentroDaValidadeIncluindoOsQueVencemHoje() {
        Cupom vigente = cupom(30, LocalDate.now().plusDays(5));
        Cupom venceHoje = cupom(20, LocalDate.now());
        Cupom vencido = cupom(10, LocalDate.now().minusDays(1));
        Cupom desativado = cupom(5, LocalDate.now().plusDays(5));
        desativado.alterarAtivo(false);
        cupomRepository.save(desativado);

        List<Long> ids = cupomService.listarDisponiveis().stream()
                .filter(c -> c.empresa().id().equals(empresa.getId()))
                .map(CupomDisponivelResponse::id)
                .toList();

        // Os mais baratos primeiro
        assertEquals(List.of(venceHoje.getId(), vigente.getId()), ids);
        assertFalse(ids.contains(vencido.getId()));
        assertFalse(ids.contains(desativado.getId()));
    }

    @Test
    void cupomVencidoOuDesativadoNaoEResgatadoNoBanco() {
        Gestante gestante = gestanteComSaldo(100);
        Cupom vencido = cupom(10, LocalDate.now().minusDays(1));

        var erro = assertThrows(ConflitoException.class, () -> cupomService.resgatar(vencido.getId(), gestante.getId()));
        assertEquals("Cupom indisponível", erro.getMessage());
        assertEquals(100, saldo(gestante));
        assertEquals(0, resgates(gestante));
    }
}
