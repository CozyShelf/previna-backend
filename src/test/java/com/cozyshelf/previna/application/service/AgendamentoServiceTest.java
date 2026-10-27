package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.domain.model.CategoriaAtendimento;
import com.cozyshelf.previna.domain.model.TipoAtendimento;
import com.cozyshelf.previna.domain.repository.TipoAtendimentoRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgendamentoServiceTest {
    private final TipoAtendimentoRepository tipoRepository = mock(TipoAtendimentoRepository.class);
    private final AgendamentoService service = new AgendamentoService(tipoRepository);

    private static TipoAtendimento tipo(Long id, String nome, CategoriaAtendimento categoria, int pontos) {
        return new TipoAtendimento(id, nome, categoria, pontos, true);
    }

    @Test
    void listaSoOsTiposAtivosDaCategoria() {
        when(tipoRepository.findByCategoriaAndAtivoTrueOrderByNomeAsc(CategoriaAtendimento.EXAME)).thenReturn(
                List.of(tipo(1L, "Hemograma completo", CategoriaAtendimento.EXAME, 25),
                        tipo(2L, "Urina tipo I (EAS)", CategoriaAtendimento.EXAME, 20)));

        var resposta = service.listarTiposAtivos(CategoriaAtendimento.EXAME);

        assertEquals(2, resposta.size());
        assertEquals("Hemograma completo", resposta.getFirst().nome());
        assertEquals(CategoriaAtendimento.EXAME, resposta.getFirst().categoria());
        assertEquals(25, resposta.getFirst().pontosBase());
        verify(tipoRepository).findByCategoriaAndAtivoTrueOrderByNomeAsc(CategoriaAtendimento.EXAME);
        verify(tipoRepository, never()).findByAtivoTrueOrderByNomeAsc();
    }

    @Test
    void semCategoriaListaTodosOsAtivos() {
        when(tipoRepository.findByAtivoTrueOrderByNomeAsc()).thenReturn(
                List.of(tipo(1L, "Consulta puerperal", CategoriaAtendimento.CONSULTA, 50),
                        tipo(2L, "Hemograma completo", CategoriaAtendimento.EXAME, 25)));

        var resposta = service.listarTiposAtivos(null);

        assertEquals(2, resposta.size());
        verify(tipoRepository).findByAtivoTrueOrderByNomeAsc();
        verify(tipoRepository, never()).findByCategoriaAndAtivoTrueOrderByNomeAsc(any());
    }
}
