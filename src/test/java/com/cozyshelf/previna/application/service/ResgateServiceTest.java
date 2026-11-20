package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.dto.response.ValidacaoCupomResponse;
import com.cozyshelf.previna.application.exception.CodigoErro;
import com.cozyshelf.previna.application.exception.ConflitoException;
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
import com.cozyshelf.previna.domain.repository.ResgateDaEmpresa;
import com.cozyshelf.previna.domain.repository.ResgateDaGestante;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    void buscaEscapaOsCuringasDoLike() {
        when(repository.daGestante(eq(7L), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.listar(7L, new ResgateRequest("50%_A\\b", null, null, null, null, null));

        // %, _ e \ são texto: vão escapados com \ para o LIKE ... ESCAPE '\'
        verify(repository).daGestante(eq(7L), isNull(), isNull(), isNull(), eq("%50\\%\\_a\\\\b%"), any());
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

    // ---- Validação do código na loja ----

    private ResgateDaEmpresa lido(CupomResgatado resgate) {
        return new ResgateDaEmpresa(resgate, Categoria.FRALDAS, "Maria");
    }

    private CupomResgatado comStatus(CupomResgatado resgate, StatusCupomResgatado status) {
        return Campos.definir(resgate, "status", status);
    }

    @Test
    void validaComOUpdateCondicionadoEOCodigoNormalizado() {
        CupomResgatado resgate = comStatus(resgateDeCupom(), StatusCupomResgatado.UTILIZADO);
        Campos.definir(resgate, "dataUso", LocalDateTime.of(2026, 11, 19, 11, 5));
        when(repository.utilizar(eq("AB12CD34"), eq(3L), any(), eq(HOJE))).thenReturn(1);
        when(repository.daEmpresa("AB12CD34", 3L)).thenReturn(Optional.of(lido(resgate)));

        ValidacaoCupomResponse validado = service.validar(3L, "  ab12cd34\t");

        assertEquals(12L, validado.id());
        assertEquals(TipoResgate.CUPOM, validado.tipo());
        assertEquals("AB12CD34", validado.codigo());
        assertNull(validado.titulo());
        assertEquals("Fralda RN", validado.produtoNome());
        assertEquals(Categoria.FRALDAS, validado.categoria());
        assertEquals(new BigDecimal("10.00"), validado.desconto());
        assertEquals(HOJE.plusDays(30), validado.validade());
        assertEquals(LocalDateTime.of(2026, 11, 19, 11, 5).atZone(ZoneId.systemDefault()).toInstant(),
                validado.dataUso());
        assertEquals("Maria", validado.gestanteNome());
        InOrder ordem = inOrder(repository);
        ordem.verify(repository).utilizar(eq("AB12CD34"), eq(3L), any(), eq(HOJE));
        ordem.verify(repository).daEmpresa("AB12CD34", 3L);
        verify(repository, never()).expirarCodigoSeVencido(any(), any(), any());
    }

    // A data de uso gravada é a hora da aplicação, e a data de hoje é a dela
    @Test
    void usaAHoraDaAplicacaoNoUpdate() {
        when(repository.utilizar(any(), any(), any(), any())).thenReturn(1);
        when(repository.daEmpresa(any(), any())).thenReturn(Optional.of(lido(resgateDeCupom())));
        LocalDateTime antes = LocalDateTime.now();

        service.validar(3L, "AB12CD34");

        var agora = ArgumentCaptor.forClass(LocalDateTime.class);
        var hoje = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).utilizar(eq("AB12CD34"), eq(3L), agora.capture(), hoje.capture());
        assertFalse(agora.getValue().isBefore(antes));
        assertFalse(agora.getValue().isAfter(LocalDateTime.now()));
        assertEquals(agora.getValue().toLocalDate(), hoje.getValue());
    }

    @Test
    void promocaoValidadaVemComOTituloECategoriaCopiados() {
        CupomResgatado resgate = resgateDePromocao();
        when(repository.utilizar(any(), any(), any(), any())).thenReturn(1);
        when(repository.daEmpresa("EF56GH78", 3L)).thenReturn(Optional.of(new ResgateDaEmpresa(resgate, null,
                "Maria")));

        ValidacaoCupomResponse validado = service.validar(3L, "EF56GH78");

        assertEquals(TipoResgate.PROMOCAO, validado.tipo());
        assertEquals("Semana do bebê", validado.titulo());
        assertNull(validado.produtoNome());
        assertEquals(Categoria.ROUPAS, validado.categoria());
    }

    // Inexistente ou de outra empresa: a consulta é sempre da empresa do token, então os dois casos são o mesmo vazio
    @Test
    void codigoQueNaoEDaEmpresaResponde404ComCodigo() {
        when(repository.daEmpresa("ZZZZ9999", 3L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.validar(3L, "zzzz9999"));

        assertEquals("Código não encontrado", erro.getMessage());
        assertEquals(CodigoErro.RESGATE_NAO_ENCONTRADO, erro.getCodigo());
        verify(repository, never()).expirarCodigoSeVencido(any(), any(), any());
    }

    @Test
    void jaUtilizadoResponde409ComADataDeUso() {
        CupomResgatado resgate = comStatus(resgateDeCupom(), StatusCupomResgatado.UTILIZADO);
        Campos.definir(resgate, "dataUso", LocalDateTime.of(2026, 11, 19, 11, 5));
        when(repository.daEmpresa("AB12CD34", 3L)).thenReturn(Optional.of(lido(resgate)));

        var erro = assertThrows(ConflitoException.class, () -> service.validar(3L, "AB12CD34"));

        assertEquals("Código já utilizado", erro.getMessage());
        assertEquals(CodigoErro.RESGATE_JA_UTILIZADO, erro.getCodigo());
        assertEquals(LocalDateTime.of(2026, 11, 19, 11, 5).atZone(ZoneId.systemDefault()).toInstant(),
                erro.getDataUso());
        verify(repository, never()).expirarCodigoSeVencido(any(), any(), any());
    }

    @Test
    void jaExpiradoResponde409SemGravarDeNovo() {
        CupomResgatado resgate = comStatus(resgateDeCupom(), StatusCupomResgatado.EXPIRADO);
        when(repository.daEmpresa("AB12CD34", 3L)).thenReturn(Optional.of(lido(resgate)));

        var erro = assertThrows(ConflitoException.class, () -> service.validar(3L, "AB12CD34"));

        assertEquals("Código expirado", erro.getMessage());
        assertEquals(CodigoErro.RESGATE_EXPIRADO, erro.getCodigo());
        assertNull(erro.getDataUso());
        verify(repository, never()).expirarCodigoSeVencido(any(), any(), any());
    }

    // Vencido e ainda ATIVO: o UPDATE não o validou; grava EXPIRADO (condicionado a ATIVO) antes de responder
    @Test
    void ativoVencidoGravaExpiradoEResponde409() {
        CupomResgatado resgate = Campos.definir(resgateDeCupom(), "dataValidade", HOJE.minusDays(1));
        when(repository.daEmpresa("AB12CD34", 3L)).thenReturn(Optional.of(lido(resgate)));

        var erro = assertThrows(ConflitoException.class, () -> service.validar(3L, "AB12CD34"));

        assertEquals(CodigoErro.RESGATE_EXPIRADO, erro.getCodigo());
        InOrder ordem = inOrder(repository);
        ordem.verify(repository).utilizar(eq("AB12CD34"), eq(3L), any(), eq(HOJE));
        ordem.verify(repository).daEmpresa("AB12CD34", 3L);
        ordem.verify(repository).expirarCodigoSeVencido("AB12CD34", 3L, HOJE);
    }

    // O resgate só ficou visível depois do UPDATE (comitado entre os dois comandos): tenta o UPDATE de novo
    @Test
    void ativoValidoDepoisDoUpdateTentaDeNovo() {
        CupomResgatado resgate = resgateDeCupom();
        when(repository.utilizar(any(), any(), any(), any())).thenReturn(0, 1);
        when(repository.daEmpresa("AB12CD34", 3L)).thenReturn(Optional.of(lido(resgate)));

        assertEquals(12L, service.validar(3L, "AB12CD34").id());

        verify(repository, times(2)).utilizar(eq("AB12CD34"), eq(3L), any(), eq(HOJE));
        verify(repository, never()).expirarCodigoSeVencido(any(), any(), any());
    }

    // Nunca deveria acontecer (os status não voltam a ATIVO): falha alto em vez de girar para sempre
    @Test
    void ativoValidoQueOUpdateNuncaPegaFalhaEmVezDeRepetirParaSempre() {
        when(repository.daEmpresa("AB12CD34", 3L)).thenReturn(Optional.of(lido(resgateDeCupom())));

        assertThrows(IllegalStateException.class, () -> service.validar(3L, "AB12CD34"));

        verify(repository, times(2)).utilizar(any(), any(), any(), any());
    }
}
