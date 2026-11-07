package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.TipoAtendimentoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.Campos;
import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TipoAtendimentoServiceTest {
    private static final long ANA = 7L;
    private static final long BRUNO = 8L;

    private final TipoAtendimentoRepository tipoRepository = mock(TipoAtendimentoRepository.class);
    private final TipoAtendimentoService service = new TipoAtendimentoService(tipoRepository);

    // Veio da migração V11: sem dono, logo visível para todo médico
    private final TipoAtendimento padrao = tipo(1L, null, "Hemograma completo", CategoriaAtendimento.EXAME);
    private final TipoAtendimento daAna = tipo(2L, ANA, "Ultrassom 4D", CategoriaAtendimento.EXAME);

    private static TipoAtendimento tipo(long id, Long dono, String nome, CategoriaAtendimento categoria) {
        return Campos.definir(new TipoAtendimento(dono, nome, categoria, 25), "id", id);
    }

    private static TipoAtendimentoRequest dados(String nome, CategoriaAtendimento categoria) {
        return new TipoAtendimentoRequest(nome, categoria, 30);
    }

    @Test
    void cadastraComOMedicoDoTokenComoDono() {
        when(tipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var resposta = service.cadastrar(ANA, dados("Ultrassom 4D", CategoriaAtendimento.EXAME));

        assertEquals("Ultrassom 4D", resposta.nome());
        assertEquals(CategoriaAtendimento.EXAME, resposta.categoria());
        assertEquals(30, resposta.pontosBase());
        // Não é padrão: a tela usa isso para liberar a edição
        assertFalse(resposta.padrao());
    }

    @Test
    void cadastraSemOsEspacosDasPontasDoNome() {
        when(tipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("Ultrassom 4D",
                service.cadastrar(ANA, dados("  Ultrassom 4D  ", CategoriaAtendimento.EXAME)).nome());
    }

    @Test
    void recusaNomeRepetidoDoMesmoMedico() {
        when(tipoRepository.existsByNomeAndCriadoPorAndAtivoTrue("Ultrassom 4D", ANA)).thenReturn(true);

        var erro = assertThrows(ConflitoException.class,
                () -> service.cadastrar(ANA, dados("Ultrassom 4D", CategoriaAtendimento.EXAME)));

        assertEquals("Você já tem um tipo com esse nome", erro.getMessage());
        verify(tipoRepository, never()).save(any());
    }

    // O nome só é único dentro do mesmo dono: dois médicos podem ter o mesmo exame na lista
    @Test
    void aceitaOMesmoNomeDeOutroMedico() {
        when(tipoRepository.existsByNomeAndCriadoPorAndAtivoTrue("Ultrassom 4D", BRUNO)).thenReturn(false);
        when(tipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("Ultrassom 4D",
                service.cadastrar(BRUNO, dados("Ultrassom 4D", CategoriaAtendimento.EXAME)).nome());
    }

    // A linha do tipo excluído continua no banco, mas não reserva o nome: a consulta ignora os excluídos,
    // como o índice único da V14
    @Test
    void aceitaONomeDeUmTipoQueOMedicoExcluiu() {
        when(tipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cadastrar(ANA, dados("Ultrassom 4D", CategoriaAtendimento.EXAME));

        verify(tipoRepository).existsByNomeAndCriadoPorAndAtivoTrue("Ultrassom 4D", ANA);
        verify(tipoRepository).save(any());
    }

    @Test
    void naoEncontraTipoDeOutroMedico() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, BRUNO)).thenReturn(Optional.empty());

        var erro = assertThrows(NaoEncontradoException.class, () -> service.buscar(BRUNO, 2L));

        assertEquals("Tipo de atendimento não encontrado", erro.getMessage());
    }

    // O tipo padrão não tem dono, então nenhum médico o edita: responde como inexistente
    @Test
    void naoEditaTipoPadrao() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(1L, ANA)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class,
                () -> service.atualizar(ANA, 1L, dados("Outro nome", CategoriaAtendimento.EXAME)));
    }

    @Test
    void atualizaNomeCategoriaEPontuacao() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, ANA)).thenReturn(Optional.of(daAna));

        var resposta = service.atualizar(ANA, 2L, dados("Ultrassom morfológico", CategoriaAtendimento.CONSULTA));

        assertEquals("Ultrassom morfológico", resposta.nome());
        assertEquals(CategoriaAtendimento.CONSULTA, resposta.categoria());
        assertEquals(30, resposta.pontosBase());
        assertEquals("Ultrassom morfológico", daAna.getNome());
    }

    @Test
    void recusaRenomearParaUmNomeQueOMedicoJaUsa() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, ANA)).thenReturn(Optional.of(daAna));
        when(tipoRepository.existsByNomeAndCriadoPorAndAtivoTrue("Consulta de retorno", ANA)).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.atualizar(ANA, 2L, dados("Consulta de retorno", CategoriaAtendimento.CONSULTA)));
        assertEquals("Ultrassom 4D", daAna.getNome());
    }

    // Trocar o nome por um que o médico já usa é conflito; manter o próprio nome não é
    @Test
    void atualizaMantendoOProprioNome() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, ANA)).thenReturn(Optional.of(daAna));

        service.atualizar(ANA, 2L, dados("Ultrassom 4D", CategoriaAtendimento.CONSULTA));

        assertEquals(CategoriaAtendimento.CONSULTA, daAna.getCategoria());
        verify(tipoRepository, never()).existsByNomeAndCriadoPorAndAtivoTrue(any(), any());
    }

    // Exclusão lógica: a linha fica, para o histórico da gestante não perder o nome do que ela fez
    @Test
    void excluirApenasMarcaOTipoComoExcluido() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, ANA)).thenReturn(Optional.of(daAna));

        service.excluir(ANA, 2L);

        assertFalse(daAna.isAtivo());
        verify(tipoRepository, never()).delete(any(TipoAtendimento.class));
    }

    // Tipo já excluído some pela mesma consulta que esconde o de outro médico: responde 404
    @Test
    void naoExcluiDuasVezes() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, ANA)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.excluir(ANA, 2L));
    }

    @Test
    void naoExcluiTipoDeOutroMedico() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, BRUNO)).thenReturn(Optional.empty());

        assertThrows(NaoEncontradoException.class, () -> service.excluir(BRUNO, 2L));
        assertTrue(daAna.isAtivo());
    }

    @Test
    void disponiveisTrazOsPadraoMaisOsDoMedico() {
        when(tipoRepository.disponiveisPara(ANA)).thenReturn(List.of(padrao, daAna));

        var resposta = service.disponiveis(ANA, null);

        assertEquals(List.of("Hemograma completo", "Ultrassom 4D"),
                resposta.stream().map(r -> r.nome()).toList());
        assertTrue(resposta.getFirst().padrao());
        assertFalse(resposta.getLast().padrao());
    }

    @Test
    void disponiveisFiltraPelaCategoria() {
        var consulta = tipo(3L, ANA, "Consulta de retorno", CategoriaAtendimento.CONSULTA);
        when(tipoRepository.disponiveisPara(ANA)).thenReturn(List.of(padrao, daAna, consulta));

        var resposta = service.disponiveis(ANA, CategoriaAtendimento.CONSULTA);

        assertEquals(List.of("Consulta de retorno"), resposta.stream().map(r -> r.nome()).toList());
    }

    // A gestão mostra só o que é do médico; os padrão não entram, porque ele não os edita
    @Test
    void listagemSoTrazOsTiposDoMedico() {
        when(tipoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(daAna)));

        var pagina = service.listar(ANA, new ListagemRequest(null, null, null), null);

        assertEquals(1, pagina.total());
        assertEquals("Ultrassom 4D", pagina.itens().getFirst().nome());
    }

    @Test
    void aceitaTipoPadraoNoCadastroDeQualquerMedico() {
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(padrao));

        assertSame(padrao, service.paraAgendamento(BRUNO, 1L));
    }

    // Isolamento: o tipo de um médico não pode ser usado por outro, nem informando o id direto na API
    @Test
    void recusaTipoDeOutroMedicoNoCadastro() {
        when(tipoRepository.findById(2L)).thenReturn(Optional.of(daAna));

        var erro = assertThrows(DadosInvalidosException.class, () -> service.paraAgendamento(BRUNO, 2L));

        assertEquals("Tipo de atendimento inválido", erro.getMessage());
    }

    // Um tipo excluído some da lista de escolha, mas o id continua existindo
    @Test
    void recusaTipoExcluidoNoCadastro() {
        var excluido = tipo(4L, ANA, "Exame descontinuado", CategoriaAtendimento.EXAME);
        excluido.excluir();
        when(tipoRepository.findById(4L)).thenReturn(Optional.of(excluido));

        assertThrows(DadosInvalidosException.class, () -> service.paraAgendamento(ANA, 4L));
    }

    @Test
    void recusaTipoInexistenteNoCadastro() {
        when(tipoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DadosInvalidosException.class, () -> service.paraAgendamento(ANA, 99L));
    }

    // O nome é aparado antes de comparar e de gravar: " Ultrassom 4D " é o mesmo tipo
    @Test
    void renomeiaParaUmNomeLivreAparandoOsEspacos() {
        when(tipoRepository.findByIdAndCriadoPorAndAtivoTrue(2L, ANA)).thenReturn(Optional.of(daAna));

        var resposta = service.atualizar(ANA, 2L, dados("  Ultrassom morfológico ", CategoriaAtendimento.EXAME));

        verify(tipoRepository).existsByNomeAndCriadoPorAndAtivoTrue("Ultrassom morfológico", ANA);
        assertEquals("Ultrassom morfológico", resposta.nome());
        assertEquals("Ultrassom morfológico", daAna.getNome());
    }
}
