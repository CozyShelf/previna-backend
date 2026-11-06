package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.CupomRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.Categoria;
import com.cozyshelf.previna.domain.model.Cupom;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.Produto;
import com.cozyshelf.previna.domain.model.StatusCupomResgatado;
import com.cozyshelf.previna.domain.repository.CupomRepository;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CupomServiceTest {
    private static final LocalDate VALIDADE = LocalDate.now().plusDays(30);

    private final CupomRepository cupomRepository = mock(CupomRepository.class);
    private final ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
    private final CupomResgatadoRepository resgatadoRepository = mock(CupomResgatadoRepository.class);
    private final EmpresaParceiraRepository empresaRepository = mock(EmpresaParceiraRepository.class);
    private final PontuacaoService pontuacaoService = mock(PontuacaoService.class);
    private final CupomService service = new CupomService(cupomRepository, produtoRepository, resgatadoRepository,
            empresaRepository, pontuacaoService);

    private final Produto fralda = new Produto(1L, "Fralda RN", null, Categoria.FRALDAS, new BigDecimal("39.90"));

    private final EmpresaParceira farmacia = Campos.definir(
            new EmpresaParceira("Farmácia Vida", "12345678000190", null, null, null, "vida", "hash"), "id", 1L);

    private Gestante gestanteComSaldo(int saldo) {
        var medico = new Medico("Dra. Ana", "11111111111", null, "CRM-SP 1", "ana", "hash", false);
        var gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash", medico,
                LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());
        return Campos.definir(Campos.definir(gestante, "id", 7L), "saldoPontos", saldo);
    }

    private Cupom cupomDe(int pontos) {
        return new Cupom(1L, fralda, BigDecimal.TEN, VALIDADE, pontos);
    }

    private static CupomRequest dados(Long produto, String desconto) {
        return new CupomRequest(produto, new BigDecimal(desconto), VALIDADE, 200);
    }

    @Test
    void cadastraParaUmProdutoDaEmpresaAtivoEComADescricaoMontada() {
        when(produtoRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(fralda));
        when(cupomRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resposta = service.cadastrar(1L, dados(10L, "12.50"));

        var salvo = ArgumentCaptor.forClass(Cupom.class);
        verify(cupomRepository).save(salvo.capture());
        assertSame(fralda, salvo.getValue().getProduto());
        assertEquals("12,5% de desconto em Fralda RN", resposta.descricao());
        assertEquals("Fralda RN", resposta.produto());
        assertTrue(resposta.ativo());
    }

    @Test
    void descricaoSemCasasDecimaisQuandoODescontoEInteiro() {
        when(produtoRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(fralda));
        when(cupomRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("100% de desconto em Fralda RN", service.cadastrar(1L, dados(10L, "100.00")).descricao());
    }

    @Test
    void recusaProdutoDeOutraEmpresa() {
        // O produto 99 é de outra empresa: a busca filtrada pela empresa do token não o acha
        when(produtoRepository.findByIdAndEmpresaId(99L, 1L)).thenReturn(Optional.empty());

        var erro = assertThrows(DadosInvalidosException.class, () -> service.cadastrar(1L, dados(99L, "10")));
        assertEquals("Produto não pertence à empresa", erro.getMessage());
        verify(cupomRepository, never()).save(any());
    }

    @Test
    void desativaEReativaCupomDaEmpresa() {
        var cupom = new Cupom(1L, fralda, BigDecimal.TEN, VALIDADE, 200);
        when(cupomRepository.findByIdAndEmpresaId(5L, 1L)).thenReturn(Optional.of(cupom));

        assertFalse(service.alterarAtivo(1L, 5L, false).ativo());
        assertTrue(service.alterarAtivo(1L, 5L, true).ativo());
    }

    @Test
    void naoAlteraCupomDeOutraEmpresa() {
        when(cupomRepository.findByIdAndEmpresaId(5L, 2L)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.alterarAtivo(2L, 5L, false));
    }

    @Test
    void listaDisponiveisSoAtivosDentroDaValidadeComAEmpresa() {
        var cupom = cupomDe(200);
        when(cupomRepository.findByAtivoTrueAndDataValidadeGreaterThanEqual(eq(LocalDate.now()), any(Sort.class)))
                .thenReturn(List.of(cupom));
        when(empresaRepository.findAllById(List.of(1L))).thenReturn(List.of(farmacia));

        var disponiveis = service.listarDisponiveis();

        assertEquals(1, disponiveis.size());
        assertEquals("Farmácia Vida", disponiveis.getFirst().empresa().nome());
        assertEquals("Fralda RN", disponiveis.getFirst().produto());
        assertEquals(Categoria.FRALDAS, disponiveis.getFirst().categoria());
        assertEquals(200, disponiveis.getFirst().pontosNecessarios());
    }

    @Test
    void resgataGerandoCodigoEDebitandoOsPontos() {
        var cupom = cupomDe(200);
        var gestante = gestanteComSaldo(500);
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupom));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestante);
        when(resgatadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(pontuacaoService.debitarPontos(eq(gestante), eq(200), any())).thenReturn(300);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(farmacia));

        var resposta = service.resgatar(5L, 7L);

        var salvo = ArgumentCaptor.forClass(CupomResgatado.class);
        verify(resgatadoRepository).save(salvo.capture());
        assertSame(cupom, salvo.getValue().getCupom());
        assertSame(gestante, salvo.getValue().getGestante());
        assertEquals(StatusCupomResgatado.ATIVO, salvo.getValue().getStatus());
        assertTrue(resposta.codigo().matches("[A-HJ-KMNP-Z2-9]{8}"));
        assertEquals(300, resposta.saldo());
        assertEquals(200, resposta.pontosGastos());
        assertEquals("Farmácia Vida", resposta.empresa());
        verify(pontuacaoService).debitarPontos(gestante, 200, salvo.getValue());
    }

    @Test
    void sorteiaOutroCodigoQuandoOPrimeiroJaExiste() {
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(100)));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(100));
        when(resgatadoRepository.existsByCodigo(any())).thenReturn(true, false);
        when(resgatadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resgatar(5L, 7L);

        verify(resgatadoRepository, times(2)).existsByCodigo(any());
    }

    @Test
    void naoResgataCupomInexistente() {
        when(cupomRepository.findById(5L)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.resgatar(5L, 7L));
        assertEquals("Cupom não encontrado", erro.getMessage());
        verifyNoInteractions(pontuacaoService, resgatadoRepository);
    }

    @Test
    void naoResgataCupomDesativadoPelaEmpresa() {
        var cupom = cupomDe(100);
        cupom.alterarAtivo(false);
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupom));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(500));

        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L));
        assertEquals("Cupom indisponível", erro.getMessage());
        verify(resgatadoRepository, never()).save(any());
    }

    @Test
    void naoResgataCupomVencido() {
        var vencido = new Cupom(1L, fralda, BigDecimal.TEN, LocalDate.now().minusDays(1), 100);
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(vencido));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(500));

        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L));
        assertEquals("Cupom indisponível", erro.getMessage());
    }

    @Test
    void cupomQueVenceHojeAindaPodeSerResgatado() {
        assertTrue(new Cupom(1L, fralda, BigDecimal.TEN, LocalDate.now(), 100).disponivelEm(LocalDate.now()));
    }

    @Test
    void naoResgataComPontosInsuficientes() {
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(200)));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(199));

        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L));
        assertEquals("Pontos insuficientes", erro.getMessage());
        verify(resgatadoRepository, never()).save(any());
        verify(pontuacaoService, never()).debitarPontos(any(), anyInt(), any());
    }

    @Test
    void resgataQuandoOSaldoEExatamenteOCusto() {
        var gestante = gestanteComSaldo(200);
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(200)));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestante);
        when(resgatadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(pontuacaoService.debitarPontos(eq(gestante), eq(200), any())).thenReturn(0);

        assertEquals(0, service.resgatar(5L, 7L).saldo());
    }

    @Test
    void naoResgataComSaldoZerado() {
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(1)));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(0));

        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L));
        assertEquals("Pontos insuficientes", erro.getMessage());
    }

    @Test
    void cupomIndisponivelVemAntesDePontosInsuficientes() {
        // Mesma ordem do diagrama: sem saldo e com o cupom fora do ar, a mensagem é a do cupom
        var cupom = cupomDe(500);
        cupom.alterarAtivo(false);
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupom));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(0));

        var erro = assertThrows(ConflitoException.class, () -> service.resgatar(5L, 7L));
        assertEquals("Cupom indisponível", erro.getMessage());
    }

    @Test
    void gestanteInexistenteNaoResgata() {
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(100)));
        when(pontuacaoService.travarGestante(7L)).thenThrow(new NaoEncontradoException("Gestante não encontrada"));

        assertThrows(NaoEncontradoException.class, () -> service.resgatar(5L, 7L));
        verify(resgatadoRepository, never()).save(any());
    }

    @Test
    void travaAGestanteAntesDeConferirOSaldo() {
        // O saldo conferido tem de ser o da linha travada: sem a trava, dois resgates leriam o mesmo saldo
        var gestante = gestanteComSaldo(500);
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(100)));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestante);
        when(resgatadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resgatar(5L, 7L);

        var ordem = inOrder(pontuacaoService, resgatadoRepository);
        ordem.verify(pontuacaoService).travarGestante(7L);
        ordem.verify(resgatadoRepository).save(any());
        ordem.verify(pontuacaoService).debitarPontos(eq(gestante), eq(100), any());
    }

    @Test
    void codigosSemCaracteresAmbiguosEDiferentesACadaResgate() {
        when(cupomRepository.findById(5L)).thenReturn(Optional.of(cupomDe(1)));
        when(pontuacaoService.travarGestante(7L)).thenReturn(gestanteComSaldo(1000));
        when(resgatadoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var codigos = new java.util.HashSet<String>();
        for (int i = 0; i < 200; i++) {
            String codigo = service.resgatar(5L, 7L).codigo();
            assertEquals(8, codigo.length());
            assertTrue(codigo.chars().noneMatch(c -> "01OIL".indexOf(c) >= 0), codigo);
            codigos.add(codigo);
        }
        assertEquals(200, codigos.size());
    }

    @Test
    void listaComADataDeHojeEOsMaisBaratosPrimeiro() {
        when(cupomRepository.findByAtivoTrueAndDataValidadeGreaterThanEqual(any(), any())).thenReturn(List.of());

        assertTrue(service.listarDisponiveis().isEmpty());

        var ordem = ArgumentCaptor.forClass(Sort.class);
        verify(cupomRepository).findByAtivoTrueAndDataValidadeGreaterThanEqual(eq(LocalDate.now()), ordem.capture());
        assertEquals(Sort.Direction.ASC, ordem.getValue().getOrderFor("pontosNecessarios").getDirection());
        assertNotNull(ordem.getValue().getOrderFor("id"));
    }

    @Test
    void listaBuscaCadaEmpresaUmaVezSo() {
        when(cupomRepository.findByAtivoTrueAndDataValidadeGreaterThanEqual(any(), any()))
                .thenReturn(List.of(cupomDe(10), cupomDe(20), cupomDe(30)));
        when(empresaRepository.findAllById(List.of(1L))).thenReturn(List.of(farmacia));

        assertEquals(3, service.listarDisponiveis().size());
        verify(empresaRepository).findAllById(List.of(1L));
    }
}
