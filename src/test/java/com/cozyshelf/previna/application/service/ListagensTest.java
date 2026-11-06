package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.ImagemProdutoRepository;
import com.cozyshelf.previna.domain.repository.LogoEmpresaRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import com.cozyshelf.previna.domain.repository.PromocaoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import com.cozyshelf.previna.infrastructure.email.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// As listagens das telas de gestão: o filtro e a ordem que cada serviço passa ao repositório, e a página que volta.
// O mais importante é o filtro pelo dono (empresa do token): sem ele, uma empresa listaria os dados de outra.
@SuppressWarnings("unchecked")
class ListagensTest {
    private static final long EMPRESA = 5L;

    private final EmpresaParceiraRepository empresaRepository = mock(EmpresaParceiraRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final PromocaoRepository promocaoRepository = mock(PromocaoRepository.class);
    private final CupomRepository cupomRepository = mock(CupomRepository.class);

    private final EmpresaService empresaService = new EmpresaService(mock(UsuarioRepository.class), empresaRepository,
            mock(LogoEmpresaRepository.class), new CredencialService(), new BCryptPasswordEncoder(4),
            mock(EmailService.class));
    private final MedicoService medicoService = new MedicoService(mock(UsuarioRepository.class), medicoRepository,
            new BCryptPasswordEncoder(4));
    private final ProdutoService produtoService = new ProdutoService(produtoRepository,
            mock(ImagemProdutoRepository.class));
    private final PromocaoService promocaoService = new PromocaoService(promocaoRepository);
    private final CupomService cupomService = new CupomService(cupomRepository, produtoRepository,
            mock(CupomResgatadoRepository.class), empresaRepository, mock(PontuacaoService.class));

    private static final ListagemRequest PADRAO = new ListagemRequest(null, null, null);

    // Ordem fixa no servidor: o campo sem diferenciar maiúsculas e o id para desempatar
    private static Pageable pagina(int numero, int tamanho, String campo) {
        return PageRequest.of(numero, tamanho, Sort.by(Sort.Order.asc(campo).ignoreCase(), Sort.Order.asc("id")));
    }

    // ---- Empresas (administrador) ----

    @Test
    void empresasPorRazaoSocialComBuscaNoTextoENoCnpj() {
        var empresa = Campos.definir(new EmpresaParceira("Farmácia Vida", "11222333000181", "contato@vida.com",
                "1133334444", "Carla", "11222333000181", "hash"), "id", 3L);
        when(empresaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(empresa), pagina(1, 10, "razaoSocial"), 11));

        var resposta = empresaService.listar(new ListagemRequest("11.222", 1, 10), StatusConta.ATIVA);

        var filtro = ArgumentCaptor.forClass(Specification.class);
        verify(empresaRepository).findAll(filtro.capture(), eq(pagina(1, 10, "razaoSocial")));
        var aplicado = FiltroAplicado.de(filtro.getValue());
        assertEquals(Map.of("statusConta", StatusConta.ATIVA), aplicado.igualdades);
        // Termo só de números procura também no CNPJ, sem a pontuação
        assertEquals("%11222%", aplicado.buscas.get("cnpj"));
        assertTrue(aplicado.buscas.keySet().containsAll(List.of("razaoSocial", "responsavel", "email")));

        assertEquals(11, resposta.total());
        assertEquals(1, resposta.pagina());
        var item = resposta.itens().getFirst();
        assertEquals("Farmácia Vida", item.razaoSocial());
        assertTrue(item.primeiroAcessoPendente());
    }

    @Test
    void empresasSemFiltroNaoRestringemNada() {
        when(empresaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        empresaService.listar(PADRAO, null);

        var filtro = ArgumentCaptor.forClass(Specification.class);
        verify(empresaRepository).findAll(filtro.capture(), eq(pagina(0, 20, "razaoSocial")));
        var aplicado = FiltroAplicado.de(filtro.getValue());
        assertTrue(aplicado.igualdades.isEmpty());
        assertTrue(aplicado.buscas.isEmpty());
    }

    // ---- Médicos (administrador) ----

    @Test
    void medicosPorNomeFiltrandoAPermissaoDeVerTodas() {
        var medico = Campos.definir(new Medico("Ana Souza", "52998224725", "ana@clinica.com", "CRM-SP 1",
                "ana.souza", "hash", true), "id", 2L);
        when(medicoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(medico), pagina(0, 20, "nome"), 1));

        var resposta = medicoService.listar(new ListagemRequest("ana", null, null), true);

        var filtro = ArgumentCaptor.forClass(Specification.class);
        verify(medicoRepository).findAll(filtro.capture(), eq(pagina(0, 20, "nome")));
        var aplicado = FiltroAplicado.de(filtro.getValue());
        assertEquals(Map.of("verTodasGestantes", true), aplicado.igualdades);
        // Termo com letras não procura no CPF
        assertEquals(List.of("nome", "crm", "email", "login"), List.copyOf(aplicado.buscas.keySet()));
        assertEquals("%ana%", aplicado.buscas.get("nome"));

        var item = resposta.itens().getFirst();
        assertEquals(2L, item.id());
        assertEquals("ana.souza", item.login());
        assertTrue(item.verTodasGestantes());
    }

    // ---- Produtos, promoções e cupons: sempre só os da empresa do token ----

    @Test
    void produtosDaEmpresaPorNomeEFiltradosPelaCategoria() {
        var produto = Campos.definir(new Produto(EMPRESA, "Fralda RN", null, Categoria.FRALDAS,
                new BigDecimal("39.90")), "id", 9L);
        when(produtoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(produto), pagina(0, 20, "nome"), 1));

        var resposta = produtoService.listar(EMPRESA, new ListagemRequest("  Fralda ", null, null), Categoria.FRALDAS);

        var filtro = ArgumentCaptor.forClass(Specification.class);
        verify(produtoRepository).findAll(filtro.capture(), eq(pagina(0, 20, "nome")));
        var aplicado = FiltroAplicado.de(filtro.getValue());
        assertEquals(Map.of("empresaId", EMPRESA, "categoria", Categoria.FRALDAS), aplicado.igualdades);
        // O termo é aparado e vai em minúsculas
        assertEquals(Map.of("nome", "%fralda%"), aplicado.buscas);
        assertEquals("Fralda RN", resposta.itens().getFirst().nome());
    }

    @Test
    void promocoesDaEmpresaPorTitulo() {
        var promocao = Campos.definir(new Promocao(EMPRESA, "Semana do bebê", null, BigDecimal.TEN,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), Categoria.ROUPAS), "id", 4L);
        when(promocaoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promocao), pagina(0, 20, "titulo"), 1));

        var resposta = promocaoService.listar(EMPRESA, new ListagemRequest("semana", null, null));

        var filtro = ArgumentCaptor.forClass(Specification.class);
        verify(promocaoRepository).findAll(filtro.capture(), eq(pagina(0, 20, "titulo")));
        var aplicado = FiltroAplicado.de(filtro.getValue());
        assertEquals(Map.of("empresaId", EMPRESA), aplicado.igualdades);
        assertEquals(Map.of("titulo", "%semana%"), aplicado.buscas);
        assertEquals(Categoria.ROUPAS, resposta.itens().getFirst().categoria());
    }

    @Test
    void cuponsDaEmpresaOsQueVencemAntesPrimeiro() {
        var fralda = new Produto(EMPRESA, "Fralda RN", null, Categoria.FRALDAS, BigDecimal.TEN);
        var cupom = Campos.definir(new Cupom(EMPRESA, fralda, BigDecimal.TEN, LocalDate.of(2026, 12, 31), 100),
                "id", 1L);
        when(cupomRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cupom), pagina(0, 5, "dataValidade"), 1));

        var resposta = cupomService.listar(EMPRESA, new ListagemRequest(null, 0, 5));

        var filtro = ArgumentCaptor.forClass(Specification.class);
        verify(cupomRepository).findAll(filtro.capture(), eq(pagina(0, 5, "dataValidade")));
        assertEquals(Map.of("empresaId", EMPRESA), FiltroAplicado.de(filtro.getValue()).igualdades);
        var item = resposta.itens().getFirst();
        assertEquals("Fralda RN", item.produto());
        assertEquals(100, item.pontosNecessarios());
        assertTrue(item.ativo());
    }
}
