package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

// "Meus cupons" contra o PostgreSQL de verdade: o UPDATE que expira, o join com o produto, a busca sem acento, a ordem
// com CASE e a paginação só se provam no banco. Cada teste usa uma gestante nova, e a listagem é sempre da gestante,
// então os dados dos outros testes não aparecem. Roda só com PREVINA_TEST_DB_URL (ver ResgateCupomIntegracaoTest).
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PREVINA_TEST_DB_URL", matches = ".+")
class CupomResgatadoIntegracaoTest {
    // Dados únicos por teste: o banco não é limpo entre os testes nem entre as execuções
    private static final AtomicLong SEQUENCIA =
            new AtomicLong(System.currentTimeMillis() % 1_000_000_000L + 700_000_000L);
    private static final LocalDate HOJE = LocalDate.now();
    private static final ResgateRequest SEM_FILTRO = new ResgateRequest(null, null, null, null, null, null);

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PREVINA_TEST_DB_URL"));
    }

    @Autowired
    private ResgateService resgateService;
    @Autowired
    private GeradorCodigoResgate geradorCodigo;
    @Autowired
    private CupomResgatadoRepository resgatadoRepository;
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

    private Medico medico;
    private Gestante gestante;
    // Resgatados um depois do outro: cada novo resgate fica um minuto mais recente que o anterior
    private LocalDateTime proximoResgate;

    @BeforeEach
    void preparar() {
        long n = SEQUENCIA.incrementAndGet();
        medico = medicoRepository.save(new Medico("Dra. Ana", cpf(n), null, "CRM-R" + n, "medresg" + n, "hash",
                false));
        gestante = gestante();
        proximoResgate = LocalDateTime.now().minusDays(30);
    }

    private static String cpf(long n) {
        return "%011d".formatted(n);
    }

    private Gestante gestante() {
        long n = SEQUENCIA.incrementAndGet();
        return gestanteRepository.save(new Gestante("Maria", cpf(n), null, "gesresg" + n, "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", HOJE));
    }

    private EmpresaParceira empresa(String nome) {
        long n = SEQUENCIA.incrementAndGet();
        return empresaRepository.save(new EmpresaParceira(nome, "%014d".formatted(n), null, null, null,
                "empresg" + n, "hash"));
    }

    private Produto produto(EmpresaParceira empresa, String nome, Categoria categoria) {
        return produtoRepository.save(new Produto(empresa.getId(), nome, null, categoria, null));
    }

    // Resgate de cupom da gestante, válido até a data indicada
    private CupomResgatado cupom(Gestante dona, Produto produto, LocalDate validade) {
        EmpresaParceira empresa = empresaRepository.findById(produto.getEmpresaId()).orElseThrow();
        Cupom cupom = cupomRepository.save(new Cupom(empresa.getId(), produto, new BigDecimal("10"), validade, 50));
        return datado(resgatadoRepository.save(new CupomResgatado(cupom, empresa, dona,
                geradorCodigo.gerarCodigoUnico(), null)));
    }

    private CupomResgatado cupom(Produto produto, LocalDate validade) {
        return cupom(gestante, produto, validade);
    }

    // Resgate de promoção da gestante, válido até o fim dela
    private CupomResgatado promocao(Gestante dona, EmpresaParceira empresa, String titulo, Categoria categoria,
                                    LocalDate fim) {
        Promocao promocao = new Promocao(empresa.getId(), titulo, null, new BigDecimal("15"), fim.minusDays(10), fim,
                categoria);
        // Desativada: a exclusão da V23 não deixa duas ativas da mesma categoria no mesmo período; o resgate é cópia
        promocao.alterarAtivo(false);
        promocao = promocaoRepository.save(promocao);
        return datado(resgatadoRepository.save(new CupomResgatado(promocao, empresa, dona,
                geradorCodigo.gerarCodigoUnico(), null)));
    }

    private CupomResgatado promocao(EmpresaParceira empresa, String titulo, Categoria categoria, LocalDate fim) {
        return promocao(gestante, empresa, titulo, categoria, fim);
    }

    private CupomResgatado datado(CupomResgatado resgate) {
        proximoResgate = proximoResgate.plusMinutes(1);
        jdbc.update("UPDATE cupom_resgatado SET data_resgate = ? WHERE id = ?", proximoResgate, resgate.getId());
        return resgate;
    }

    private void marcar(CupomResgatado resgate, StatusCupomResgatado status) {
        jdbc.update("UPDATE cupom_resgatado SET status = ? WHERE id = ?", status.name(), resgate.getId());
    }

    private void resgatadoEm(CupomResgatado resgate, LocalDateTime quando) {
        jdbc.update("UPDATE cupom_resgatado SET data_resgate = ? WHERE id = ?", quando, resgate.getId());
    }

    private String status(CupomResgatado resgate) {
        return jdbc.queryForObject("SELECT status FROM cupom_resgatado WHERE id = ?", String.class, resgate.getId());
    }

    private PaginaResponse<ResgateResponse> listar(ResgateRequest filtros) {
        return resgateService.listar(gestante.getId(), filtros);
    }

    private List<Long> ids(ResgateRequest filtros) {
        return listar(filtros).itens().stream().map(ResgateResponse::id).toList();
    }

    private static List<Long> ids(CupomResgatado... resgates) {
        List<Long> ids = new ArrayList<>();
        for (CupomResgatado resgate : resgates) {
            ids.add(resgate.getId());
        }
        return ids;
    }

    private static ResgateRequest busca(String trecho) {
        return new ResgateRequest(trecho, null, null, null, null, null);
    }

    // ---- Resposta ----

    @Test
    void cupomEPromocaoComOsDadosDaCopiaEACategoriaAtualDoProduto() {
        EmpresaParceira loja = empresa("Loja X");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        CupomResgatado cupom = cupom(fralda, HOJE.plusDays(30));
        CupomResgatado promocao = promocao(loja, "Semana do bebê", Categoria.ROUPAS, HOJE.plusDays(5));
        // Depois do resgate a empresa muda o produto: o nome é o da cópia, a categoria é a atual
        jdbc.update("UPDATE produto SET nome = 'Fralda P', categoria = 'REMEDIOS' WHERE id = ?", fralda.getId());

        List<ResgateResponse> itens = listar(SEM_FILTRO).itens();

        ResgateResponse daPromocao = itens.get(0);
        assertEquals(promocao.getId(), daPromocao.id());
        assertEquals(TipoResgate.PROMOCAO, daPromocao.tipo());
        assertEquals("Semana do bebê", daPromocao.titulo());
        assertNull(daPromocao.produto());
        assertEquals(Categoria.ROUPAS, daPromocao.categoria());
        assertEquals(0, daPromocao.pontosGastos());
        assertEquals(HOJE.plusDays(5), daPromocao.dataValidade());

        ResgateResponse doCupom = itens.get(1);
        assertEquals(cupom.getId(), doCupom.id());
        assertEquals(TipoResgate.CUPOM, doCupom.tipo());
        assertEquals(cupom.getCodigo(), doCupom.codigo());
        assertEquals("10% de desconto em Fralda RN", doCupom.titulo());
        assertEquals(new ResgateResponse.Empresa(loja.getId(), "Loja X"), doCupom.empresa());
        assertEquals("Fralda RN", doCupom.produto());
        assertEquals(Categoria.REMEDIOS, doCupom.categoria());
        assertEquals(0, new BigDecimal("10").compareTo(doCupom.desconto()));
        assertEquals(StatusCupomResgatado.ATIVO, doCupom.status());
        assertNull(doCupom.dataUso());
        assertEquals(50, doCupom.pontosGastos());
    }

    // ---- Expiração na leitura ----

    @Test
    void listarExpiraOsAtivosVencidosEMantemOsDemais() {
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        CupomResgatado vencido = cupom(fralda, HOJE.minusDays(1));
        CupomResgatado venceHoje = cupom(fralda, HOJE);
        CupomResgatado utilizadoVencido = cupom(fralda, HOJE.minusDays(5));
        marcar(utilizadoVencido, StatusCupomResgatado.UTILIZADO);
        CupomResgatado promocaoVencida = promocao(loja, "Semana do bebê", Categoria.ROUPAS, HOJE.minusDays(2));

        var pagina = listar(SEM_FILTRO);

        assertEquals("EXPIRADO", status(vencido));
        assertEquals("EXPIRADO", status(promocaoVencida));
        // A validade vale até o fim do dia
        assertEquals("ATIVO", status(venceHoje));
        // A validação na loja nunca é desfeita: o UPDATE só pega ATIVO
        assertEquals("UTILIZADO", status(utilizadoVencido));
        // A própria resposta já traz o status novo
        assertEquals(List.of(StatusCupomResgatado.ATIVO, StatusCupomResgatado.EXPIRADO,
                        StatusCupomResgatado.UTILIZADO, StatusCupomResgatado.EXPIRADO),
                pagina.itens().stream().map(ResgateResponse::status).toList());
    }

    @Test
    void listarNaoExpiraOsResgatesDeOutraGestante() {
        Gestante outra = gestante();
        EmpresaParceira loja = empresa("Loja");
        CupomResgatado daOutra = cupom(outra, produto(loja, "Fralda RN", Categoria.FRALDAS), HOJE.minusDays(1));

        listar(SEM_FILTRO);

        assertEquals("ATIVO", status(daOutra));
    }

    @Test
    void detalheExpiraSoOResgateDetalhado() {
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        CupomResgatado detalhado = cupom(fralda, HOJE.minusDays(1));
        CupomResgatado outroVencido = cupom(fralda, HOJE.minusDays(1));

        ResgateResponse detalhe = resgateService.detalhar(gestante.getId(), detalhado.getId());

        assertEquals(StatusCupomResgatado.EXPIRADO, detalhe.status());
        assertEquals("EXPIRADO", status(detalhado));
        assertEquals("ATIVO", status(outroVencido));
    }

    @Test
    void detalheDeUtilizadoVencidoContinuaUtilizado() {
        CupomResgatado utilizado = cupom(produto(empresa("Loja"), "Fralda RN", Categoria.FRALDAS),
                HOJE.minusDays(3));
        marcar(utilizado, StatusCupomResgatado.UTILIZADO);

        assertEquals(StatusCupomResgatado.UTILIZADO,
                resgateService.detalhar(gestante.getId(), utilizado.getId()).status());
        assertEquals("UTILIZADO", status(utilizado));
    }

    @Test
    void detalheDeOutraGestanteOuInexistenteResponde404SemExpirar() {
        Gestante outra = gestante();
        CupomResgatado daOutra = cupom(outra, produto(empresa("Loja"), "Fralda RN", Categoria.FRALDAS),
                HOJE.minusDays(1));

        var deOutra = assertThrows(NaoEncontradoException.class,
                () -> resgateService.detalhar(gestante.getId(), daOutra.getId()));
        var inexistente = assertThrows(NaoEncontradoException.class,
                () -> resgateService.detalhar(gestante.getId(), Long.MAX_VALUE));

        // A mesma resposta nos dois casos: não revela que o id existe
        assertEquals("Resgate não encontrado", deOutra.getMessage());
        assertEquals(deOutra.getMessage(), inexistente.getMessage());
        assertEquals("ATIVO", status(daOutra));
        // A dona continua vendo
        assertEquals(daOutra.getId(), resgateService.detalhar(outra.getId(), daOutra.getId()).id());
    }

    @Test
    void detalheDeCupomTrazACategoriaAtualDoProduto() {
        Produto pomada = produto(empresa("Loja"), "Pomada", null);
        CupomResgatado resgate = cupom(pomada, HOJE.plusDays(5));

        assertNull(resgateService.detalhar(gestante.getId(), resgate.getId()).categoria());
        jdbc.update("UPDATE produto SET categoria = 'REMEDIOS' WHERE id = ?", pomada.getId());
        assertEquals(Categoria.REMEDIOS, resgateService.detalhar(gestante.getId(), resgate.getId()).categoria());
    }

    // ---- Ordem e paginação ----

    @Test
    void ativosPrimeiroDepoisUtilizadosEExpiradosJuntosDoMaisRecente() {
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        LocalDateTime base = LocalDateTime.now().minusDays(10);
        CupomResgatado ativoAntigo = cupom(fralda, HOJE.plusDays(5));
        CupomResgatado utilizado = cupom(fralda, HOJE.plusDays(5));
        CupomResgatado expirado = cupom(fralda, HOJE.plusDays(5));
        CupomResgatado ativoRecente = promocao(loja, "Semana do bebê", Categoria.ROUPAS, HOJE.plusDays(5));
        CupomResgatado vencidoRecente = cupom(fralda, HOJE.minusDays(1));
        CupomResgatado utilizadoNoMesmoInstante = cupom(fralda, HOJE.plusDays(5));
        marcar(utilizado, StatusCupomResgatado.UTILIZADO);
        marcar(expirado, StatusCupomResgatado.EXPIRADO);
        marcar(utilizadoNoMesmoInstante, StatusCupomResgatado.UTILIZADO);
        resgatadoEm(ativoAntigo, base.plusDays(1));
        resgatadoEm(utilizado, base.plusDays(3));
        resgatadoEm(expirado, base.plusDays(2));
        resgatadoEm(ativoRecente, base.plusDays(4));
        // Vencido e ainda ATIVO: vira EXPIRADO antes de ordenar, e vai para o segundo grupo
        resgatadoEm(vencidoRecente, base.plusDays(9));
        // Mesmo instante do utilizado: o id maior (resgatado depois) vem antes
        resgatadoEm(utilizadoNoMesmoInstante, base.plusDays(3));

        assertEquals(ids(ativoRecente, ativoAntigo, vencidoRecente, utilizadoNoMesmoInstante, utilizado, expirado),
                ids(SEM_FILTRO));
    }

    @Test
    void paginaComOTotalSemRepetirNemPular() {
        Produto fralda = produto(empresa("Loja"), "Fralda RN", Categoria.FRALDAS);
        List<Long> criados = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            criados.add(cupom(fralda, HOJE.plusDays(5)).getId());
        }

        var primeira = listar(new ResgateRequest(null, null, null, null, 0, 2));
        var segunda = listar(new ResgateRequest(null, null, null, null, 1, 2));
        var terceira = listar(new ResgateRequest(null, null, null, null, 2, 2));

        assertEquals(5, primeira.total());
        assertEquals(3, primeira.totalPaginas());
        assertEquals(2, primeira.tamanho());
        assertEquals(1, terceira.itens().size());
        List<Long> todas = new ArrayList<>();
        for (var pagina : List.of(primeira, segunda, terceira)) {
            pagina.itens().forEach(item -> todas.add(item.id()));
        }
        // Do mais recente para o mais antigo
        assertEquals(criados.reversed(), todas);
        // O total também respeita os filtros
        assertEquals(0, listar(new ResgateRequest(null, null, TipoResgate.PROMOCAO, null, 0, 2)).total());
        // Página padrão: 20
        assertEquals(20, listar(SEM_FILTRO).tamanho());
    }

    @Test
    void resgatesDeOutraGestanteNaoAparecem() {
        Gestante outra = gestante();
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        CupomResgatado meu = cupom(fralda, HOJE.plusDays(5));
        cupom(outra, fralda, HOJE.plusDays(5));
        promocao(outra, loja, "Semana do bebê", Categoria.FRALDAS, HOJE.plusDays(5));

        var pagina = listar(SEM_FILTRO);

        assertEquals(ids(meu), pagina.itens().stream().map(ResgateResponse::id).toList());
        assertEquals(1, pagina.total());
    }

    // ---- Filtros ----

    @Test
    void filtraPorTipo() {
        EmpresaParceira loja = empresa("Loja");
        CupomResgatado cupom = cupom(produto(loja, "Fralda RN", Categoria.FRALDAS), HOJE.plusDays(5));
        CupomResgatado promocao = promocao(loja, "Semana do bebê", Categoria.FRALDAS, HOJE.plusDays(5));

        assertEquals(ids(cupom), ids(new ResgateRequest(null, null, TipoResgate.CUPOM, null, null, null)));
        assertEquals(ids(promocao), ids(new ResgateRequest(null, null, TipoResgate.PROMOCAO, null, null, null)));
    }

    @Test
    void filtraPorStatusDepoisDeExpirar() {
        Produto fralda = produto(empresa("Loja"), "Fralda RN", Categoria.FRALDAS);
        CupomResgatado ativo = cupom(fralda, HOJE.plusDays(5));
        CupomResgatado utilizado = cupom(fralda, HOJE.plusDays(5));
        marcar(utilizado, StatusCupomResgatado.UTILIZADO);
        CupomResgatado vencido = cupom(fralda, HOJE.minusDays(1));

        assertEquals(ids(ativo), ids(new ResgateRequest(null, null, null, StatusCupomResgatado.ATIVO, null, null)));
        assertEquals(ids(utilizado),
                ids(new ResgateRequest(null, null, null, StatusCupomResgatado.UTILIZADO, null, null)));
        // O vencido ainda gravado como ATIVO já sai como EXPIRADO, e não no filtro de ATIVO
        assertEquals(ids(vencido),
                ids(new ResgateRequest(null, null, null, StatusCupomResgatado.EXPIRADO, null, null)));
    }

    @Test
    void filtraPorCategoriaAtualDoProdutoNoCupomECopiadaNaPromocao() {
        EmpresaParceira loja = empresa("Loja");
        Produto fralda = produto(loja, "Fralda RN", Categoria.FRALDAS);
        Produto pomada = produto(loja, "Pomada", null);
        CupomResgatado cupomFralda = cupom(fralda, HOJE.plusDays(5));
        CupomResgatado cupomPomada = cupom(pomada, HOJE.plusDays(5));
        CupomResgatado promocaoRoupas = promocao(loja, "Semana do bebê", Categoria.ROUPAS, HOJE.plusDays(5));
        CupomResgatado promocaoFraldas = promocao(loja, "Mês da fralda", Categoria.FRALDAS, HOJE.plusDays(5));

        assertEquals(ids(promocaoFraldas, cupomFralda),
                ids(new ResgateRequest(null, Categoria.FRALDAS, null, null, null, null)));
        assertEquals(ids(promocaoRoupas), ids(new ResgateRequest(null, Categoria.ROUPAS, null, null, null, null)));
        assertTrue(ids(new ResgateRequest(null, Categoria.REMEDIOS, null, null, null, null)).isEmpty());

        // O produto passa a ser de remédios: o cupom muda de categoria; a promoção de fraldas não
        jdbc.update("UPDATE produto SET categoria = 'REMEDIOS' WHERE id = ?", fralda.getId());
        assertEquals(ids(cupomFralda), ids(new ResgateRequest(null, Categoria.REMEDIOS, null, null, null, null)));
        assertEquals(ids(promocaoFraldas), ids(new ResgateRequest(null, Categoria.FRALDAS, null, null, null, null)));
        // Produto sem categoria não entra em nenhuma
        jdbc.update("UPDATE produto SET categoria = 'ROUPAS' WHERE id = ?", pomada.getId());
        assertEquals(ids(promocaoRoupas, cupomPomada),
                ids(new ResgateRequest(null, Categoria.ROUPAS, null, null, null, null)));
    }

    @Test
    void buscaNoNomeDaEmpresaSemAcentoNemMaiusculas() {
        EmpresaParceira farmacia = empresa("Farmácia Vida");
        EmpresaParceira loja = empresa("Loja do Bebê");
        CupomResgatado daFarmacia = cupom(produto(farmacia, "Pomada", null), HOJE.plusDays(5));
        CupomResgatado promocaoDaFarmacia = promocao(farmacia, "Semana do bebê", Categoria.REMEDIOS,
                HOJE.plusDays(5));
        cupom(produto(loja, "Body", Categoria.ROUPAS), HOJE.plusDays(5));

        assertEquals(ids(promocaoDaFarmacia, daFarmacia), ids(busca("  FARMACIA vida ")));
    }

    @Test
    void buscaNoNomeDoProdutoDoCupom() {
        EmpresaParceira loja = empresa("Loja");
        CupomResgatado lenco = cupom(produto(loja, "Lenço umedecido", Categoria.FRALDAS), HOJE.plusDays(5));
        cupom(produto(loja, "Body", Categoria.ROUPAS), HOJE.plusDays(5));
        promocao(loja, "Semana do bebê", Categoria.FRALDAS, HOJE.plusDays(5));

        assertEquals(ids(lenco), ids(busca("LENCO")));
    }

    @Test
    void buscaNoTituloDaPromocao() {
        EmpresaParceira loja = empresa("Loja");
        cupom(produto(loja, "Fralda RN", Categoria.FRALDAS), HOJE.plusDays(5));
        CupomResgatado semana = promocao(loja, "Semana do Bebê", Categoria.ROUPAS, HOJE.plusDays(5));
        promocao(loja, "Mês da fralda", Categoria.FRALDAS, HOJE.plusDays(5));

        assertEquals(ids(semana), ids(busca("semana do bebe")));
        assertTrue(ids(busca("inexistente")).isEmpty());
    }

    // % e _ digitados são texto, não curinga do LIKE: "50%" não acha tudo que tem "50", e "_" não casa qualquer
    // caractere. Vale para o nome do produto, o título da promoção e o nome da empresa
    @Test
    void buscaTrataPorcentagemESublinhadoComoTexto() {
        EmpresaParceira loja = empresa("Loja");
        EmpresaParceira sublinhado = empresa("Loja_Bebe");
        CupomResgatado cinquentaPorCento = cupom(produto(loja, "Fralda 50% off", Categoria.FRALDAS),
                HOJE.plusDays(5));
        cupom(produto(loja, "Fralda 500 unidades", Categoria.FRALDAS), HOJE.plusDays(5));
        CupomResgatado promocao = promocao(loja, "Leve 3, pague 2 (33%)", Categoria.ROUPAS, HOJE.plusDays(5));
        promocao(loja, "Semana 330", Categoria.REMEDIOS, HOJE.plusDays(5));
        CupomResgatado daSublinhado = cupom(produto(sublinhado, "Body", Categoria.ROUPAS), HOJE.plusDays(5));
        // "Loja Bebe" e "LojaXBebe" casariam com "loja_bebe" se o _ fosse curinga
        cupom(produto(empresa("Loja Bebe"), "Body", Categoria.ROUPAS), HOJE.plusDays(5));
        cupom(produto(empresa("LojaXBebe"), "Body", Categoria.ROUPAS), HOJE.plusDays(5));

        assertEquals(ids(cinquentaPorCento), ids(busca("50%")));
        assertEquals(ids(promocao), ids(busca("33%")));
        assertEquals(ids(daSublinhado), ids(busca("loja_bebe")));
        assertEquals(ids(cinquentaPorCento, promocao).reversed(), ids(busca("%")));
        assertEquals(1, listar(busca("_")).total());
    }

    @Test
    void filtrosCombinados() {
        EmpresaParceira farmacia = empresa("Farmácia Vida");
        EmpresaParceira loja = empresa("Loja");
        Produto fraldaFarmacia = produto(farmacia, "Fralda RN", Categoria.FRALDAS);
        CupomResgatado alvo = cupom(fraldaFarmacia, HOJE.plusDays(5));
        CupomResgatado utilizado = cupom(fraldaFarmacia, HOJE.plusDays(5));
        marcar(utilizado, StatusCupomResgatado.UTILIZADO);
        cupom(produto(farmacia, "Body", Categoria.ROUPAS), HOJE.plusDays(5));
        cupom(produto(loja, "Fralda P", Categoria.FRALDAS), HOJE.plusDays(5));
        promocao(farmacia, "Fraldas em dobro", Categoria.FRALDAS, HOJE.plusDays(5));

        var filtros = new ResgateRequest("farmacia", Categoria.FRALDAS, TipoResgate.CUPOM,
                StatusCupomResgatado.ATIVO, null, null);
        assertEquals(ids(alvo), ids(filtros));
        assertEquals(1, listar(filtros).total());
        // Busca no título e tipo PROMOCAO juntos
        assertEquals(1, listar(new ResgateRequest("dobro", Categoria.FRALDAS, TipoResgate.PROMOCAO, null, null,
                null)).total());
        assertEquals(0, listar(new ResgateRequest("dobro", Categoria.FRALDAS, TipoResgate.CUPOM, null, null,
                null)).total());
    }
}
