package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.domain.model.Escolaridade;
import com.cozyshelf.previna.domain.model.FotoGestante;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.FotoGestanteRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.HexFormat;
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
    private final FotoGestanteRepository fotoRepository = mock(FotoGestanteRepository.class);
    private final MedicoRepository medicoRepository = mock(MedicoRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final GestanteService service = new GestanteService(usuarioRepository, gestanteRepository,
            medicoRepository, new CredencialService(), passwordEncoder, dadosSocioeconomicosRepository,
            fotoRepository);

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
        service.concluirPrimeiroAcesso(1L, primeiroAcesso("maria.silva", "novaSenha1"), null);

        assertEquals("maria.silva", gestante.getLogin());
        assertTrue(passwordEncoder.matches("novaSenha1", gestante.getSenhaHash()));
        assertFalse(gestante.primeiroAcessoPendente());
        verify(dadosSocioeconomicosRepository).save(argThat(d -> d.getEscolaridade() == Escolaridade.MEDIO_COMPLETO));
    }

    @Test
    void recusaLoginEmUso() {
        when(usuarioRepository.existsByLogin("medico")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.concluirPrimeiroAcesso(1L, primeiroAcesso("medico", "novaSenha1"), null));
        assertTrue(gestante.primeiroAcessoPendente());
        verify(dadosSocioeconomicosRepository, never()).save(any());
    }

    @Test
    void recusaSegundoPrimeiroAcesso() {
        service.concluirPrimeiroAcesso(1L, primeiroAcesso("maria.silva", "novaSenha1"), null);

        assertThrows(ConflitoException.class,
                () -> service.concluirPrimeiroAcesso(1L, primeiroAcesso("outro.login", "outraSenha2"), null));
    }

    private static Medico medico(boolean verTodasGestantes) {
        return new Medico("Ana Souza", "52998224725", "ana@clinica.com", "CRM-SP 123456", "ana.souza", "hash",
                verTodasGestantes);
    }

    private static final ListagemRequest PRIMEIRA_PAGINA = new ListagemRequest(null, null, null);

    @Test
    void listaGestantesPaginadasComONomeDoMedico() {
        Medico medico = medico(false);
        Gestante daAna = new Gestante("Maria", "12345678909", null, "12345678909", "hash",
                medico, LocalDate.of(1995, 1, 1), "11999999999", LocalDate.now());
        when(medicoRepository.findById(7L)).thenReturn(Optional.of(medico));
        when(gestanteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(daAna), PageRequest.of(0, 20), 41));

        var pagina = service.listar(7L, PRIMEIRA_PAGINA, null, false);

        assertEquals(41, pagina.total());
        assertEquals(3, pagina.totalPaginas());
        var item = pagina.itens().get(0);
        assertEquals("Maria", item.nome());
        assertEquals("Ana Souza", item.medico());
        assertEquals(daAna.getDum().plusDays(280), item.dataProvavelParto());
        assertTrue(item.primeiroAcessoPendente());
    }

    @Test
    void semPermissaoOMedicoSoVeAsProprias() {
        Medico medico = medico(false);

        assertTrue(filtraPeloMedico(medico, false));
    }

    @Test
    void comPermissaoOMedicoVeTodasOuEscolheSoAsProprias() {
        Medico medico = medico(true);

        assertFalse(filtraPeloMedico(medico, false));
        assertTrue(filtraPeloMedico(medico, true));
    }

    // Monta o filtro passado ao repositório e confere se ele restringe ao médico
    @SuppressWarnings("unchecked")
    private boolean filtraPeloMedico(Medico medico, boolean somenteMinhas) {
        clearInvocations(gestanteRepository);
        when(medicoRepository.findById(7L)).thenReturn(Optional.of(medico));
        when(gestanteRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.listar(7L, PRIMEIRA_PAGINA, null, somenteMinhas);

        ArgumentCaptor<Specification<Gestante>> filtro = ArgumentCaptor.forClass(Specification.class);
        verify(gestanteRepository).findAll(filtro.capture(), any(Pageable.class));
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        filtro.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), cb);
        return mockingDetails(cb).getInvocations().stream()
                .anyMatch(i -> i.getMethod().getName().equals("equal") && i.getArgument(1) == medico);
    }

    @Test
    void concluiTutorial() {
        service.concluirTutorial(1L);

        assertFalse(gestante.tutorialPendente());
    }

    private static MockMultipartFile foto(String hex) {
        return new MockMultipartFile("foto", "foto.jpg", "image/jpeg", HexFormat.of().parseHex(hex));
    }

    @Test
    void guardaFotoDoPrimeiroAcesso() {
        service.concluirPrimeiroAcesso(1L, primeiroAcesso("maria.silva", "novaSenha1"), foto("ffd8ffe000104a46"));

        var salva = ArgumentCaptor.forClass(FotoGestante.class);
        verify(fotoRepository).save(salva.capture());
        assertEquals("image/jpeg", salva.getValue().getTipo());
    }

    @Test
    void recusaFotoQueNaoEImagemSemConcluirPrimeiroAcesso() {
        assertThrows(DadosInvalidosException.class, () -> service.concluirPrimeiroAcesso(1L,
                primeiroAcesso("maria.silva", "novaSenha1"), foto("3c68746d6c3e")));
        assertTrue(gestante.primeiroAcessoPendente());
        verify(fotoRepository, never()).save(any());
    }

    @Test
    void atualizaContatoComEmailVazioComoNuloERemoveFoto() {
        var perfil = service.atualizarPerfil(1L, new AtualizacaoPerfilGestanteRequest("11912345678", "", true), null);

        verify(fotoRepository).deleteById(1L);
        verify(usuarioRepository, never()).existsByEmail(any());
        assertEquals("11912345678", perfil.telefone());
        assertNull(perfil.email());
    }

    @Test
    void recusaEmailDeOutroUsuario() {
        when(usuarioRepository.existsByEmail("medico@previna.com")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> service.atualizarPerfil(1L,
                new AtualizacaoPerfilGestanteRequest("11912345678", "medico@previna.com", false), null));
        assertEquals("11999999999", gestante.getTelefone());
    }
}
