package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.Escolaridade;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class GestanteServiceTest {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final GestanteRepository gestanteRepository = mock(GestanteRepository.class);
    private final DadosSocioeconomicosRepository dadosSocioeconomicosRepository = mock(DadosSocioeconomicosRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final GestanteService service = new GestanteService(usuarioRepository, gestanteRepository,
            mock(MedicoRepository.class), new CredencialService(), passwordEncoder, dadosSocioeconomicosRepository);

    private final Gestante gestante = new Gestante("Maria", "12345678909", null, "12345678909", "hash",
            null, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());

    @BeforeEach
    void setUp() {
        when(gestanteRepository.findById(1L)).thenReturn(Optional.of(gestante));
    }

    private static PrimeiroAcessoRequest primeiroAcesso(String login, String senha) {
        return new PrimeiroAcessoRequest(login, senha, true, null, null, Escolaridade.MEDIO_COMPLETO, null);
    }

    @Test
    void trocaCredenciaisRegistraDadosEAtivaConta() {
        service.concluirPrimeiroAcesso(1L, primeiroAcesso("maria.silva", "novaSenha1"));

        assertEquals("maria.silva", gestante.getLogin());
        assertTrue(passwordEncoder.matches("novaSenha1", gestante.getSenhaHash()));
        assertFalse(gestante.primeiroAcessoPendente());
        verify(dadosSocioeconomicosRepository).save(argThat(d -> d.getEscolaridade() == Escolaridade.MEDIO_COMPLETO));
    }

    @Test
    void recusaLoginEmUso() {
        when(usuarioRepository.existsByLogin("medico")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.concluirPrimeiroAcesso(1L, primeiroAcesso("medico", "novaSenha1")));
        assertTrue(gestante.primeiroAcessoPendente());
        verify(dadosSocioeconomicosRepository, never()).save(any());
    }

    @Test
    void recusaSegundoPrimeiroAcesso() {
        service.concluirPrimeiroAcesso(1L, primeiroAcesso("maria.silva", "novaSenha1"));

        assertThrows(ConflitoException.class,
                () -> service.concluirPrimeiroAcesso(1L, primeiroAcesso("outro.login", "outraSenha2")));
    }

    @Test
    void listaGestantesDoMedico() {
        when(gestanteRepository.findByMedicoIdOrderByNomeAsc(7L)).thenReturn(List.of(gestante));

        var lista = service.listarDoMedico(7L);

        assertEquals(1, lista.size());
        assertEquals("Maria", lista.get(0).nome());
        assertEquals("12345678909", lista.get(0).cpf());
        assertEquals(gestante.getDum().plusDays(280), lista.get(0).dataProvavelParto());
        assertTrue(lista.get(0).primeiroAcessoPendente());
    }

    @Test
    void concluiTutorial() {
        service.concluirTutorial(1L);

        assertFalse(gestante.tutorialPendente());
    }
}
