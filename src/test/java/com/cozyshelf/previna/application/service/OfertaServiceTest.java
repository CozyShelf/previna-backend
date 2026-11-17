package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.OfertaRequest;
import com.cozyshelf.previna.application.dto.response.ProdutoOfertaResumoResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.TipoOferta;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// A montagem do catálogo: páginas, selos de cada produto, saldo da gestante e os 404. Se os filtros escolhem os
// produtos certos no banco é com o OfertaIntegracaoTest.
@SuppressWarnings("unchecked")
class OfertaServiceTest {
    private static final LocalDate HOJE = LocalDate.now();

    private final EmpresaParceiraRepository empresaRepository = mock(EmpresaParceiraRepository.class);
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final CupomRepository cupomRepository = mock(CupomRepository.class);
    private final PromocaoRepository promocaoRepository = mock(PromocaoRepository.class);
    private final GestanteRepository gestanteRepository = mock(GestanteRepository.class);
    private final CupomResgatadoRepository resgatadoRepository = mock(CupomResgatadoRepository.class);
    private final OfertaService service = new OfertaService(empresaRepository, produtoRepository, cupomRepository,
            promocaoRepository, gestanteRepository, resgatadoRepository);

    private final EmpresaParceira farmacia = empresa(1L, "Farmácia Vida");
    private final EmpresaParceira loja = empresa(2L, "Loja do Bebê");
    private final Produto fralda = produto(10L, 1L, "Fralda RN", Categoria.FRALDAS);
    private final Produto pomada = produto(11L, 1L, "Pomada", null);
    private final Produto body = produto(20L, 2L, "Body", Categoria.ROUPAS);

    private static final OfertaRequest SEM_FILTRO = new OfertaRequest(null, null, null, null, null, null, null);

    private static EmpresaParceira empresa(Long id, String nome) {
        var empresa = Campos.definir(new EmpresaParceira(nome, "12345678000190", null, null, null, "e" + id, "hash"),
                "id", id);
        empresa.completarCadastro(null, null, "Farmácia");
        empresa.alterarLogo("/empresas/" + id + "/logo?v=1");
        return empresa;
    }

    private static Produto produto(Long id, Long empresa, String nome, Categoria categoria) {
        return Campos.definir(new Produto(empresa, nome, null, categoria, null), "id", id);
    }

    private static Cupom cupom(Long id, Produto produto, String desconto, int pontos) {
        return Campos.definir(new Cupom(produto.getEmpresaId(), produto, new BigDecimal(desconto), HOJE.plusDays(5),
                pontos), "id", id);
    }

    private static Promocao promocao(Long id, Long empresa, Categoria categoria, String desconto) {
        return Campos.definir(new Promocao(empresa, "Semana " + id, null, new BigDecimal(desconto), HOJE,
                HOJE.plusDays(7), categoria), "id", id);
    }

    private void empresasNaPagina(EmpresaParceira... empresas) {
        when(empresaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(empresas), i.getArgument(1), empresas.length));
    }

    // A primeira chamada de produtos é da primeira empresa da página, e assim por diante. O total passa do tamanho
    // da página (10): a empresa tem mais produtos do que os mostrados
    private void produtosPorEmpresa(List<Produto>... listas) {
        var respostas = new java.util.ArrayDeque<>(List.of(listas));
        when(produtoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(i -> {
                    List<Produto> produtos = respostas.poll();
                    return new PageImpl<>(produtos, i.getArgument(1), produtos.size() + 11L);
                });
    }

    private Gestante gestanteComSaldo(int saldo) {
        var medico = new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false);
        var gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", HOJE);
        return Campos.definir(Campos.definir(gestante, "id", 7L), "saldoPontos", saldo);
    }

    // ---- Lista de empresas ----

    @Test
    void listaEmpresasComOsProdutosEOsSelosDeCada() {
        empresasNaPagina(farmacia, loja);
        produtosPorEmpresa(List.of(fralda, pomada), List.of(body));
        when(cupomRepository.validos(anyCollection(), eq(HOJE), any(Sort.class))).thenReturn(List.of(
                cupom(1L, fralda, "10", 120), cupom(2L, fralda, "25.50", 300), cupom(3L, pomada, "5", 50)));
        when(promocaoRepository.vigentes(anyCollection(), eq(HOJE), any(Sort.class))).thenReturn(List.of(
                promocao(1L, 1L, Categoria.FRALDAS, "15"), promocao(2L, 1L, Categoria.FRALDAS, "20"),
                // Mesma categoria da fralda, mas de outra empresa; e uma de roupas da própria loja
                promocao(3L, 2L, Categoria.FRALDAS, "90"), promocao(4L, 2L, Categoria.ROUPAS, "30")));

        var pagina = service.listarEmpresas(7L, SEM_FILTRO);

        assertEquals(2, pagina.itens().size());
        var primeira = pagina.itens().getFirst();
        assertEquals("Farmácia Vida", primeira.nome());
        assertEquals("Farmácia", primeira.segmento());
        assertEquals("/empresas/1/logo?v=1", primeira.logo());
        assertEquals(13, primeira.totalProdutos());

        ProdutoOfertaResumoResponse resumoFralda = primeira.produtos().getFirst();
        assertEquals("Fralda RN", resumoFralda.nome());
        assertEquals(Categoria.FRALDAS, resumoFralda.categoria());
        assertEquals(120, resumoFralda.menorPontos());
        assertEquals(new BigDecimal("25.50"), resumoFralda.maiorDescontoCupom());
        assertEquals(new BigDecimal("20"), resumoFralda.descontoPromocao());

        // Sem categoria: cupom sim, promoção nunca
        ProdutoOfertaResumoResponse resumoPomada = primeira.produtos().get(1);
        assertEquals(50, resumoPomada.menorPontos());
        assertNull(resumoPomada.descontoPromocao());

        // Só promoção: sem os selos de cupom
        ProdutoOfertaResumoResponse resumoBody = pagina.itens().get(1).produtos().getFirst();
        assertNull(resumoBody.menorPontos());
        assertNull(resumoBody.maiorDescontoCupom());
        assertEquals(new BigDecimal("30"), resumoBody.descontoPromocao());
    }

    @Test
    void buscaOsSelosDeTodosOsProdutosDaPaginaDeUmaVez() {
        empresasNaPagina(farmacia, loja);
        produtosPorEmpresa(List.of(fralda, pomada), List.of(body));

        service.listarEmpresas(7L, SEM_FILTRO);

        var produtos = ArgumentCaptor.forClass(Collection.class);
        var empresas = ArgumentCaptor.forClass(Collection.class);
        verify(cupomRepository).validos(produtos.capture(), eq(HOJE), any(Sort.class));
        verify(promocaoRepository).vigentes(empresas.capture(), eq(HOJE), any(Sort.class));
        assertEquals(List.of(10L, 11L, 20L), produtos.getValue().stream().sorted().toList());
        assertEquals(List.of(1L, 2L), empresas.getValue().stream().sorted().toList());
    }

    @Test
    void empresasPorNomeDezPorPaginaEAteDezProdutosPorNome() {
        empresasNaPagina(farmacia);
        produtosPorEmpresa(List.of(fralda));

        service.listarEmpresas(7L, SEM_FILTRO);

        var paginaEmpresas = ArgumentCaptor.forClass(Pageable.class);
        verify(empresaRepository).findAll(any(Specification.class), paginaEmpresas.capture());
        assertEquals(PageRequest.of(0, 10, Sort.by("razaoSocial", "id")), paginaEmpresas.getValue());
        var paginaProdutos = ArgumentCaptor.forClass(Pageable.class);
        verify(produtoRepository).findAll(any(Specification.class), paginaProdutos.capture());
        assertEquals(PageRequest.of(0, OfertaService.PRODUTOS_POR_EMPRESA, Sort.by("nome", "id")),
                paginaProdutos.getValue());
    }

    @Test
    void respeitaAPaginaEOTamanhoPedidos() {
        empresasNaPagina();

        var pagina = service.listarEmpresas(7L, new OfertaRequest(null, null, null, null, null, 2, 5));

        assertEquals(2, pagina.pagina());
        assertEquals(5, pagina.tamanho());
        assertTrue(pagina.itens().isEmpty());
        verifyNoInteractions(produtoRepository);
    }

    @Test
    void semCabeNoSaldoNaoLeAGestante() {
        empresasNaPagina();

        service.listarEmpresas(7L, new OfertaRequest("fralda", Categoria.FRALDAS, 1L, TipoOferta.CUPOM, false,
                null, null));

        verifyNoInteractions(gestanteRepository);
    }

    @Test
    void cabeNoSaldoLeOSaldoDaGestanteDoToken() {
        when(gestanteRepository.findById(7L)).thenReturn(Optional.of(gestanteComSaldo(150)));
        empresasNaPagina();

        service.listarEmpresas(7L, new OfertaRequest(null, null, null, null, true, null, null));

        verify(gestanteRepository).findById(7L);
    }

    @Test
    void gestanteDoTokenQueNaoExisteMaisResponde404() {
        when(gestanteRepository.findById(7L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class,
                () -> service.listarEmpresas(7L, new OfertaRequest(null, null, null, null, true, null, null)));
        assertEquals("Gestante não encontrada", erro.getMessage());
        verifyNoInteractions(empresaRepository);
    }

    // ---- Ver todos de uma empresa ----

    @Test
    void verTodosListaOsProdutosDaEmpresaPorNome() {
        when(empresaRepository.existsById(1L)).thenReturn(true);
        produtosPorEmpresa(List.of(fralda, pomada));
        when(cupomRepository.validos(anyCollection(), eq(HOJE), any(Sort.class)))
                .thenReturn(List.of(cupom(1L, fralda, "10", 120)));
        when(promocaoRepository.vigentes(anyCollection(), eq(HOJE), any(Sort.class))).thenReturn(List.of());

        var pagina = service.listarProdutos(7L, 1L, new OfertaRequest(null, null, null, null, null, 1, 2));

        assertEquals(List.of("Fralda RN", "Pomada"), pagina.itens().stream().map(ProdutoOfertaResumoResponse::nome)
                .toList());
        assertEquals(120, pagina.itens().getFirst().menorPontos());
        assertNull(pagina.itens().get(1).menorPontos());
        var paginaProdutos = ArgumentCaptor.forClass(Pageable.class);
        verify(produtoRepository).findAll(any(Specification.class), paginaProdutos.capture());
        assertEquals(PageRequest.of(1, 2, Sort.by("nome", "id")), paginaProdutos.getValue());
    }

    @Test
    void verTodosDeEmpresaInexistenteResponde404() {
        when(empresaRepository.existsById(99L)).thenReturn(false);

        var erro = assertThrows(NaoEncontradoException.class, () -> service.listarProdutos(7L, 99L, SEM_FILTRO));
        assertEquals("Empresa não encontrada", erro.getMessage());
        verifyNoInteractions(produtoRepository);
    }

    // ---- Detalhe do produto ----

    @Test
    void detalheTrazTodosOsCuponsEAsPromocoesDaCategoria() {
        fralda.alterarImagem("/empresas/produtos/10/imagem?v=1");
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(fralda));
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(farmacia));
        when(cupomRepository.validos(List.of(10L), HOJE, Sort.by("pontosNecessarios", "id")))
                .thenReturn(List.of(cupom(1L, fralda, "10", 120), cupom(2L, fralda, "25", 300)));
        when(promocaoRepository.vigentes(List.of(1L), HOJE,
                Sort.by(Sort.Order.desc("desconto"), Sort.Order.asc("id"))))
                .thenReturn(List.of(promocao(5L, 1L, Categoria.FRALDAS, "20"), promocao(6L, 1L, Categoria.ROUPAS, "50")));

        var detalhe = service.detalhar(7L, 10L);

        assertEquals("Fralda RN", detalhe.nome());
        assertEquals(Categoria.FRALDAS, detalhe.categoria());
        assertEquals("/empresas/produtos/10/imagem?v=1", detalhe.imagem());
        assertEquals("Farmácia Vida", detalhe.empresa().nome());
        assertEquals("/empresas/1/logo?v=1", detalhe.empresa().logo());
        assertEquals(List.of(1L, 2L), detalhe.cupons().stream().map(c -> c.id()).toList());
        assertEquals(120, detalhe.cupons().getFirst().pontosNecessarios());
        assertEquals(HOJE.plusDays(5), detalhe.cupons().getFirst().dataValidade());
        // A promoção de roupas não vale para a fralda
        assertEquals(1, detalhe.promocoes().size());
        assertEquals("Semana 5", detalhe.promocoes().getFirst().titulo());
        assertEquals(new BigDecimal("20"), detalhe.promocoes().getFirst().desconto());
        assertEquals(HOJE.plusDays(7), detalhe.promocoes().getFirst().dataFim());
    }

    @Test
    void detalheSoComPromocao() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(fralda));
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(farmacia));
        when(cupomRepository.validos(anyCollection(), any(), any(Sort.class))).thenReturn(List.of());
        when(promocaoRepository.vigentes(anyCollection(), any(), any(Sort.class)))
                .thenReturn(List.of(promocao(5L, 1L, Categoria.FRALDAS, "20")));

        var detalhe = service.detalhar(7L, 10L);

        assertTrue(detalhe.cupons().isEmpty());
        assertEquals(1, detalhe.promocoes().size());
    }

    // resgatada vem de uma consulta só, com as promoções que valem para o produto, e é da gestante do token
    @Test
    void detalheDizQuaisPromocoesAGestanteJaResgatou() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(fralda));
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(farmacia));
        when(cupomRepository.validos(anyCollection(), any(), any(Sort.class))).thenReturn(List.of());
        when(promocaoRepository.vigentes(anyCollection(), any(), any(Sort.class))).thenReturn(List.of(
                promocao(5L, 1L, Categoria.FRALDAS, "20"), promocao(6L, 1L, Categoria.FRALDAS, "10"),
                promocao(8L, 1L, Categoria.ROUPAS, "50")));
        when(resgatadoRepository.promocoesResgatadas(7L, List.of(5L, 6L))).thenReturn(java.util.Set.of(6L));

        var detalhe = service.detalhar(7L, 10L);

        assertEquals(List.of(false, true), detalhe.promocoes().stream().map(p -> p.resgatada()).toList());
        verify(resgatadoRepository, times(1)).promocoesResgatadas(any(), any());
    }

    @Test
    void detalheSemPromocaoNaoConsultaOsResgates() {
        when(produtoRepository.findById(10L)).thenReturn(Optional.of(fralda));
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(farmacia));
        when(cupomRepository.validos(anyCollection(), any(), any(Sort.class)))
                .thenReturn(List.of(cupom(1L, fralda, "10", 120)));
        when(promocaoRepository.vigentes(anyCollection(), any(), any(Sort.class))).thenReturn(List.of());

        assertTrue(service.detalhar(7L, 10L).promocoes().isEmpty());
        verify(resgatadoRepository, never()).promocoesResgatadas(any(), any());
    }

    @Test
    void detalheDeProdutoInexistenteResponde404() {
        when(produtoRepository.findById(99L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.detalhar(7L, 99L));
        assertEquals("Produto não encontrado", erro.getMessage());
    }

    @Test
    void detalheDeProdutoSemOfertaResponde404ComoInexistente() {
        // Sem categoria, a promoção da empresa não vale para o produto, e ele não tem cupom
        when(produtoRepository.findById(11L)).thenReturn(Optional.of(pomada));
        when(cupomRepository.validos(anyCollection(), any(), any(Sort.class))).thenReturn(List.of());
        when(promocaoRepository.vigentes(anyCollection(), any(), any(Sort.class)))
                .thenReturn(List.of(promocao(5L, 1L, Categoria.FRALDAS, "20")));

        var erro = assertThrows(NaoEncontradoException.class, () -> service.detalhar(7L, 11L));
        assertEquals("Produto não encontrado", erro.getMessage());
        verify(empresaRepository, never()).findById(any());
    }

    // ---- Regra da promoção ----

    @Test
    void promocaoValeSoParaProdutoDaMesmaEmpresaEDaMesmaCategoria() {
        var promocao = promocao(1L, 1L, Categoria.FRALDAS, "10");

        assertTrue(promocao.valePara(fralda));
        assertFalse(promocao.valePara(pomada));
        assertFalse(promocao.valePara(produto(30L, 1L, "Body", Categoria.ROUPAS)));
        assertFalse(promocao.valePara(produto(31L, 2L, "Fralda P", Categoria.FRALDAS)));
    }
}
