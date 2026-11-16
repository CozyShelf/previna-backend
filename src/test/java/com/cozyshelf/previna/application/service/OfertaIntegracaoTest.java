package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.OfertaRequest;
import com.cozyshelf.previna.application.dto.response.OfertaEmpresaResponse;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaResumoResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.TipoOferta;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

// O catálogo de ofertas contra o PostgreSQL de verdade: as subconsultas (cupom válido, promoção vigente, nome da
// empresa), a busca sem acento e a ordem só se provam no banco. Mesmo esquema do ResgateCupomIntegracaoTest: roda só
// com PREVINA_TEST_DB_URL apontando para um banco descartável.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class OfertaIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes nem entre as execuções. Cada teste filtra pelo
    // sufixo das suas empresas (busca no nome da empresa), para não ver as ofertas dos outros.
    private static final AtomicLong SEQUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);
    private static final LocalDate HOJE = LocalDate.now();

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private OfertaService ofertaService;
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

    private String sufixo;

    @BeforeEach
    void preparar() {
        sufixo = letras(SEQUENCIA.incrementAndGet());
    }

    // Sufixo só de letras: não colide com outro teste e não cai em nenhum nome de produto
    private static String letras(long n) {
        StringBuilder letras = new StringBuilder("zq");
        for (long resto = n; resto > 0; resto /= 26) {
            letras.append((char) ('a' + resto % 26));
        }
        return letras.toString();
    }

    // Prefixo 6 nos documentos separa estes dos gerados pelos outros testes de integração
    private EmpresaParceira empresa(String nome) {
        long n = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(new EmpresaParceira(nome + " " + sufixo, "6%013d".formatted(n), null, null,
                null, "oferta" + n, "hash"));
    }

    private Produto produto(EmpresaParceira empresa, String nome, Categoria categoria) {
        return produtoRepository.save(new Produto(empresa.getId(), nome, null, categoria, null));
    }

    private Cupom cupom(Produto produto, String desconto, int pontos, LocalDate validade) {
        return cupomRepository.save(new Cupom(produto.getEmpresaId(), produto, new BigDecimal(desconto), validade,
                pontos));
    }

    private Cupom cupom(Produto produto, int pontos) {
        return cupom(produto, "10", pontos, HOJE.plusDays(5));
    }

    private Promocao promocao(EmpresaParceira empresa, Categoria categoria, String desconto, LocalDate inicio,
                              LocalDate fim) {
        return promocaoRepository.save(new Promocao(empresa.getId(), "Semana do bebê", null,
                new BigDecimal(desconto), inicio, fim, categoria));
    }

    private Gestante gestanteComSaldo(int saldo) {
        long n = SEQUENCIA.incrementAndGet();
        Medico medico = medicoRepository.save(new Medico("Dra. Ana", "6%010d".formatted(n), null, "CRM-O" + n,
                "medoferta" + n, "hash", false));
        Gestante gestante = gestanteRepository.save(new Gestante("Maria", "6%010d".formatted(n + 1), null,
                "gesoferta" + n, "hash", medico, LocalDate.of(1995, 1, 1), "11999999999", HOJE));
        SEQUENCIA.incrementAndGet();
        jdbc.update("UPDATE gestante SET saldo_pontos = ? WHERE usuario_id = ?", saldo, gestante.getId());
        return gestante;
    }

    private OfertaRequest filtro(TipoOferta tipo, Categoria categoria, Boolean ateMeusPontos) {
        return new OfertaRequest(sufixo, categoria, null, tipo, ateMeusPontos, null, null);
    }

    private List<OfertaEmpresaResponse> empresas(OfertaRequest filtros) {
        return ofertaService.listarEmpresas(0L, filtros).itens();
    }

    private static List<String> nomes(List<ProdutoOfertaResumoResponse> produtos) {
        return produtos.stream().map(ProdutoOfertaResumoResponse::nome).toList();
    }

    // ---- O que é oferta ----

    @Test
    void soAparecemProdutosComCupomValidoOuPromocaoVigente() {
        EmpresaParceira loja = empresa("Loja");
        cupom(produto(loja, "Fralda RN", Categoria.FRALDAS), 100);
        cupom(produto(loja, "Lenço", Categoria.FRALDAS), "10", 80, HOJE); // vence hoje: ainda vale
        cupom(produto(loja, "Mamadeira", null), 50); // sem categoria, mas com cupom
        cupom(produto(loja, "Chupeta", null), "10", 50, HOJE.minusDays(1)); // vencido
        Cupom desativado = cupom(produto(loja, "Babador", null), 50);
        desativado.alterarAtivo(false);
        cupomRepository.save(desativado);
        produto(loja, "Body", Categoria.ROUPAS); // só a promoção de roupas
        produto(loja, "Xarope", Categoria.REMEDIOS); // promoção de remédios só começa amanhã
        produto(loja, "Pomada", null); // sem categoria: a promoção de nenhuma categoria vale
        promocao(loja, Categoria.ROUPAS, "30", HOJE, HOJE);
        promocao(loja, Categoria.REMEDIOS, "30", HOJE.plusDays(1), HOJE.plusDays(9));
        promocao(loja, Categoria.FRALDAS, "40", HOJE.minusDays(9), HOJE.minusDays(1)); // já terminou

        List<OfertaEmpresaResponse> empresas = empresas(filtro(null, null, null));

        assertEquals(1, empresas.size());
        assertEquals("Loja " + sufixo, empresas.getFirst().nome());
        assertEquals(List.of("Body", "Fralda RN", "Lenço", "Mamadeira"), nomes(empresas.getFirst().produtos()));
        assertEquals(4, empresas.getFirst().totalProdutos());
        var body = empresas.getFirst().produtos().getFirst();
        assertNull(body.menorPontos());
        assertEquals(0, new BigDecimal("30").compareTo(body.descontoPromocao()));
        var fralda = empresas.getFirst().produtos().get(1);
        assertEquals(100, fralda.menorPontos());
        // A promoção de fraldas já terminou
        assertNull(fralda.descontoPromocao());
    }

    @Test
    void produtoSemCategoriaTemCupomMasNuncaPromocao() {
        EmpresaParceira loja = empresa("Loja");
        Produto semCategoria = produto(loja, "Pomada", null);
        cupom(semCategoria, 60);
        for (Categoria categoria : Categoria.values()) {
            promocao(loja, categoria, "50", HOJE, HOJE.plusDays(5));
        }

        var produtos = empresas(filtro(null, null, null)).getFirst().produtos();
        assertEquals(List.of("Pomada"), nomes(produtos));
        assertNull(produtos.getFirst().descontoPromocao());
        assertTrue(empresas(filtro(TipoOferta.PROMOCAO, null, null)).isEmpty());
        assertTrue(ofertaService.detalhar(semCategoria.getId()).promocoes().isEmpty());
    }

    @Test
    void promocaoDeOutraEmpresaNaoValeParaOProduto() {
        EmpresaParceira loja = empresa("Loja");
        EmpresaParceira outra = empresa("Outra");
        produto(loja, "Fralda RN", Categoria.FRALDAS);
        promocao(outra, Categoria.FRALDAS, "20", HOJE, HOJE.plusDays(5));

        assertTrue(empresas(filtro(null, null, null)).isEmpty());
    }

    @Test
    void empresasPorNomeESemOfertaNaoAparece() {
        EmpresaParceira beta = empresa("Beta");
        EmpresaParceira alfa = empresa("Alfa");
        EmpresaParceira semOferta = empresa("Gama");
        cupom(produto(beta, "Fralda RN", Categoria.FRALDAS), 100);
        cupom(produto(alfa, "Body", Categoria.ROUPAS), 100);
        produto(semOferta, "Fralda P", Categoria.FRALDAS);

        var pagina = ofertaService.listarEmpresas(0L, new OfertaRequest(sufixo, null, null, null, null, 0, 1));

        assertEquals(List.of("Alfa " + sufixo), pagina.itens().stream().map(OfertaEmpresaResponse::nome).toList());
        assertEquals(2, pagina.total());
        assertEquals(2, pagina.totalPaginas());
        assertEquals(List.of("Beta " + sufixo), ofertaService.listarEmpresas(0L,
                new OfertaRequest(sufixo, null, null, null, null, 1, 1)).itens().stream()
                .map(OfertaEmpresaResponse::nome).toList());
    }

    @Test
    void ateDezProdutosPorEmpresaComOTotalEOVerTodosComORestante() {
        EmpresaParceira loja = empresa("Loja");
        for (int i = 12; i >= 1; i--) {
            cupom(produto(loja, "Produto %02d".formatted(i), Categoria.FRALDAS), 10 * i);
        }

        var empresa = empresas(filtro(null, null, null)).getFirst();
        assertEquals(12, empresa.totalProdutos());
        assertEquals(10, empresa.produtos().size());
        assertEquals("Produto 01", empresa.produtos().getFirst().nome());
        assertEquals("Produto 10", empresa.produtos().getLast().nome());

        var segunda = ofertaService.listarProdutos(0L, loja.getId(),
                new OfertaRequest(null, null, null, null, null, 1, null));
        assertEquals(List.of("Produto 11", "Produto 12"), nomes(segunda.itens()));
        assertEquals(12, segunda.total());
        assertEquals(10, segunda.tamanho());
    }

    // ---- Filtros ----

    @Test
    void filtraPorTipoDeOferta() {
        EmpresaParceira loja = empresa("Loja");
        cupom(produto(loja, "Fralda RN", Categoria.FRALDAS), 100);
        produto(loja, "Body", Categoria.ROUPAS);
        cupom(produto(loja, "Macacão", Categoria.ROUPAS), 100);
        promocao(loja, Categoria.ROUPAS, "15", HOJE, HOJE.plusDays(5));

        assertEquals(List.of("Fralda RN", "Macacão"),
                nomes(empresas(filtro(TipoOferta.CUPOM, null, null)).getFirst().produtos()));
        var soPromocao = empresas(filtro(TipoOferta.PROMOCAO, null, null)).getFirst().produtos();
        assertEquals(List.of("Body", "Macacão"), nomes(soPromocao));
        // O selo do cupom continua no resumo, mesmo filtrando por promoção
        assertEquals(100, soPromocao.get(1).menorPontos());
    }

    @Test
    void filtraPorCategoria() {
        EmpresaParceira loja = empresa("Loja");
        cupom(produto(loja, "Fralda RN", Categoria.FRALDAS), 100);
        cupom(produto(loja, "Body", Categoria.ROUPAS), 100);
        cupom(produto(loja, "Pomada", null), 100);

        assertEquals(List.of("Body"), nomes(empresas(filtro(null, Categoria.ROUPAS, null)).getFirst().produtos()));
        assertTrue(empresas(filtro(null, Categoria.REMEDIOS, null)).isEmpty());
    }

    @Test
    void filtraPorEmpresa() {
        EmpresaParceira alfa = empresa("Alfa");
        EmpresaParceira beta = empresa("Beta");
        cupom(produto(alfa, "Fralda RN", Categoria.FRALDAS), 100);
        cupom(produto(beta, "Body", Categoria.ROUPAS), 100);

        // Busca em branco não restringe: a empresa já isola o teste
        var empresas = empresas(new OfertaRequest("   ", null, beta.getId(), null, null, null, null));

        assertEquals(List.of(beta.getId()), empresas.stream().map(OfertaEmpresaResponse::id).toList());
    }

    @Test
    void buscaNoNomeDoProdutoOuDaEmpresaSemAcentoNemMaiusculas() {
        EmpresaParceira loja = empresa("Farmácia");
        cupom(produto(loja, "Lenço umedecido " + sufixo, Categoria.FRALDAS), 100);
        cupom(produto(loja, "Body", Categoria.ROUPAS), 100);

        // Pelo nome do produto: só ele
        var porProduto = empresas(new OfertaRequest("LENCO UMEDECIDO " + sufixo.toUpperCase(), null, null, null,
                null, null, null));
        assertEquals(List.of("Lenço umedecido " + sufixo), nomes(porProduto.getFirst().produtos()));
        // Pelo nome da empresa: todos os produtos dela
        var porEmpresa = empresas(new OfertaRequest("  farmacia " + sufixo + " ", null, null, null, null, null,
                null));
        assertEquals(2, porEmpresa.getFirst().totalProdutos());
    }

    @Test
    void cabeNoSaldoSoMostraProdutosComCupomQueAGestantePodeResgatar() {
        Gestante gestante = gestanteComSaldo(100);
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        cupom(fralda, 300);
        cupom(fralda, 100); // exatamente o saldo
        cupom(produto(loja, "Body", Categoria.ROUPAS), 101);
        cupom(produto(loja, "Lenço", Categoria.FRALDAS), "10", 10, HOJE.minusDays(1)); // barato, mas vencido
        produto(loja, "Macacão", Categoria.ROUPAS);
        promocao(loja, Categoria.ROUPAS, "15", HOJE, HOJE.plusDays(5)); // promoção não tem custo em pontos

        var produtos = ofertaService.listarEmpresas(gestante.getId(), filtro(null, null, true)).itens().getFirst()
                .produtos();

        assertEquals(List.of("Fralda RN"), nomes(produtos));
        assertEquals(100, produtos.getFirst().menorPontos());
        var verTodos = ofertaService.listarProdutos(gestante.getId(), loja.getId(), filtro(null, null, true));
        assertEquals(List.of("Fralda RN"), nomes(verTodos.itens()));
    }

    // ---- Ver todos e detalhe ----

    @Test
    void verTodosDeEmpresaSemOfertaVemVazioEDeEmpresaInexistenteResponde404() {
        EmpresaParceira semOferta = empresa("Loja");
        produto(semOferta, "Fralda RN", Categoria.FRALDAS);

        assertTrue(ofertaService.listarProdutos(0L, semOferta.getId(), filtro(null, null, null)).itens().isEmpty());
        assertThrows(NaoEncontradoException.class,
                () -> ofertaService.listarProdutos(0L, Long.MAX_VALUE, filtro(null, null, null)));
    }

    @Test
    void verTodosIgnoraOutraEmpresaNaQuery() {
        EmpresaParceira alfa = empresa("Alfa");
        EmpresaParceira beta = empresa("Beta");
        cupom(produto(alfa, "Fralda RN", Categoria.FRALDAS), 100);
        cupom(produto(beta, "Body", Categoria.ROUPAS), 100);

        var produtos = ofertaService.listarProdutos(0L, alfa.getId(),
                new OfertaRequest(null, null, beta.getId(), null, null, null, null));

        assertEquals(List.of("Fralda RN"), nomes(produtos.itens()));
    }

    @Test
    void detalheComTodosOsCuponsValidosDoMaisBaratoEAsPromocoesVigentes() {
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        Cupom caro = cupom(fralda, "25", 300, HOJE.plusDays(30));
        Cupom barato = cupom(fralda, "10", 120, HOJE);
        cupom(fralda, "50", 10, HOJE.minusDays(1));
        Promocao menor = promocao(loja, Categoria.FRALDAS, "10", HOJE, HOJE.plusDays(5));
        Promocao maior = promocao(loja, Categoria.FRALDAS, "20", HOJE.minusDays(5), HOJE);
        promocao(loja, Categoria.ROUPAS, "50", HOJE, HOJE.plusDays(5));

        var detalhe = ofertaService.detalhar(fralda.getId());

        assertEquals("Loja " + sufixo, detalhe.empresa().nome());
        assertEquals(List.of(barato.getId(), caro.getId()), detalhe.cupons().stream()
                .map(c -> c.id()).toList());
        assertEquals(List.of(maior.getId(), menor.getId()), detalhe.promocoes().stream()
                .map(p -> p.id()).toList());
    }

    @Test
    void detalheDeProdutoSemOfertaOuInexistenteResponde404() {
        EmpresaParceira loja = empresa("Loja");
        Produto semOferta = produto(loja, "Fralda RN", Categoria.FRALDAS);
        cupom(semOferta, "10", 10, HOJE.minusDays(1));

        var erro = assertThrows(NaoEncontradoException.class, () -> ofertaService.detalhar(semOferta.getId()));
        assertEquals("Produto não encontrado", erro.getMessage());
        assertThrows(NaoEncontradoException.class, () -> ofertaService.detalhar(Long.MAX_VALUE));
    }
}
