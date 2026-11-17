package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.Promocao;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.model.TipoResgate;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.ResgateDaGestante;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

// A montagem de "meus cupons": a expiração antes da leitura, os filtros passados ao banco e a resposta de cada tipo.
// Se a consulta filtra, ordena e expira as linhas certas é com o CupomResgatadoIntegracaoTest.
class ResgateServiceTest {
    private static final LocalDate HOJE = LocalDate.now();

    private final CupomResgatadoRepository repository = mock(CupomResgatadoRepository.class);
    private final ResgateService service = new ResgateService(repository);

    private final EmpresaParceira loja = Campos.definir(new EmpresaParceira("Loja X", "12345678000190", null, null,
            null, "loja", "hash"), "id", 3L);
    private final Gestante gestante = Campos.definir(new Gestante("Maria", "12345678909", null, "12345678909", "hash",
            null, LocalDate.of(1995, 1, 1), "11999999999", HOJE), "id", 7L);

    private static final ResgateRequest SEM_FILTRO = new ResgateRequest(null, null, null, null, null, null);

    private CupomResgatado resgateDeCupom() {
        Produto fralda = Campos.definir(new Produto(3L, "Fralda RN", null, Categoria.FRALDAS, null), "id", 10L);
        Cupom cupom = new Cupom(3L, fralda, new BigDecimal("10.00"), HOJE.plusDays(30), 120);
        return Campos.definir(new CupomResgatado(cupom, loja, gestante, "AB12CD34", null), "id", 12L);
    }

    private CupomResgatado resgateDePromocao() {
        Promocao promocao = new Promocao(3L, "Semana do bebê", null, new BigDecimal("15.00"), HOJE, HOJE.plusDays(5),
                Categoria.ROUPAS);
        CupomResgatado resgate = Campos.definir(new CupomResgatado(promocao, loja, gestante, "EF56GH78", null),
                "id", 13L);
        Campos.definir(resgate, "status", StatusCupomResgatado.UTILIZADO);
        return Campos.definir(resgate, "dataUso", LocalDateTime.of(2026, 11, 11, 12, 30));
    }

    @Test
    void expiraOsVencidosDaGestanteAntesDeListar() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listar(7L, SEM_FILTRO);

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).expirarVencidos(7L, HOJE);
        ordem.verify(repository).daGestante(eq(7L), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void semFiltroUsaAPaginaPadraoSemOrdemDoCliente() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listar(7L, SEM_FILTRO);

        // A ordem vem do ORDER BY da consulta; um Sort aqui seria acrescentado depois dele
        verify(repository).daGestante(7L, null, null, null, null, PageRequest.of(0, 20));
    }

    @Test
    void passaOsFiltrosEABuscaEmMinusculasComCuringas() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listar(7L, new ResgateRequest("  Fralda RN ", Categoria.FRALDAS, TipoResgate.CUPOM,
                StatusCupomResgatado.ATIVO, 2, 5));

        verify(repository).daGestante(7L, TipoResgate.CUPOM, StatusCupomResgatado.ATIVO, Categoria.FRALDAS,
                "%fralda rn%", PageRequest.of(2, 5));
    }

    @Test
    void buscaEmBrancoNaoRestringe() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listar(7L, new ResgateRequest("   ", null, null, null, null, null));

        verify(repository).daGestante(eq(7L), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void montaCupomComACategoriaAtualDoProduto() {
        CupomResgatado resgate = resgateDeCupom();
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new ResgateDaGestante(resgate, Categoria.REMEDIOS)), PageRequest.of(0, 20), 1));

        var pagina = service.listar(7L, SEM_FILTRO);

        assertEquals(1, pagina.total());
        ResgateResponse cupom = pagina.itens().getFirst();
        assertEquals(12L, cupom.id());
        assertEquals(TipoResgate.CUPOM, cupom.tipo());
        assertEquals("AB12CD34", cupom.codigo());
        assertEquals("10% de desconto em Fralda RN", cupom.titulo());
        assertEquals(new ResgateResponse.Empresa(3L, "Loja X"), cupom.empresa());
        assertEquals("Fralda RN", cupom.produto());
        // A categoria vem do produto como está hoje (mudou de fraldas para remédios depois do resgate)
        assertEquals(Categoria.REMEDIOS, cupom.categoria());
        assertEquals(new BigDecimal("10.00"), cupom.desconto());
        assertEquals(HOJE.plusDays(30), cupom.dataValidade());
        assertEquals(StatusCupomResgatado.ATIVO, cupom.status());
        assertEquals(resgate.getDataResgate().atZone(ZoneId.systemDefault()).toInstant(), cupom.dataResgate());
        assertNull(cupom.dataUso());
        assertEquals(120, cupom.pontosGastos());
    }

    @Test
    void cupomDeProdutoSemCategoriaVemComCategoriaNula() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new ResgateDaGestante(resgateDeCupom(), null))));

        assertNull(service.listar(7L, SEM_FILTRO).itens().getFirst().categoria());
    }

    @Test
    void montaPromocaoComTituloECategoriaCopiadosSemProdutoNemPontos() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new ResgateDaGestante(resgateDePromocao(), null))));

        ResgateResponse promocao = service.listar(7L, SEM_FILTRO).itens().getFirst();

        assertEquals(TipoResgate.PROMOCAO, promocao.tipo());
        assertEquals("Semana do bebê", promocao.titulo());
        assertNull(promocao.produto());
        assertEquals(Categoria.ROUPAS, promocao.categoria());
        assertEquals(0, promocao.pontosGastos());
        assertEquals(StatusCupomResgatado.UTILIZADO, promocao.status());
        assertEquals(LocalDateTime.of(2026, 11, 11, 12, 30).atZone(ZoneId.systemDefault()).toInstant(),
                promocao.dataUso());
    }

    @Test
    void detalheExpiraSoEsseResgateAntesDeLer() {
        when(repository.daGestante(12L, 7L)).thenReturn(Optional.of(new ResgateDaGestante(resgateDeCupom(),
                Categoria.FRALDAS)));

        ResgateResponse detalhe = service.detalhar(7L, 12L);

        assertEquals("AB12CD34", detalhe.codigo());
        InOrder ordem = inOrder(repository);
        ordem.verify(repository).expirarSeVencido(12L, 7L, HOJE);
        ordem.verify(repository).daGestante(12L, 7L);
        verify(repository, never()).expirarVencidos(any(), any());
    }

    @Test
    void detalheDeResgateInexistenteOuDeOutraGestanteResponde404() {
        when(repository.daGestante(99L, 7L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.detalhar(7L, 99L));

        assertEquals("Resgate não encontrado", erro.getMessage());
    }
}
