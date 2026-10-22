package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.PreCadastroGestanteRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.dto.response.CredenciaisTemporariasResponse;
import com.cozyshelf.previna.application.dto.response.GestanteResumoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.DadosSocioeconomicos;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GestanteService {
    private final UsuarioRepository usuarioRepository;
    private final GestanteRepository gestanteRepository;
    private final MedicoRepository medicoRepository;
    private final CredencialService credencialService;
    private final PasswordEncoder passwordEncoder;
    private final DadosSocioeconomicosRepository dadosSocioeconomicosRepository;

    public GestanteService(UsuarioRepository usuarioRepository, GestanteRepository gestanteRepository,
                           MedicoRepository medicoRepository, CredencialService credencialService,
                           PasswordEncoder passwordEncoder, DadosSocioeconomicosRepository dadosSocioeconomicosRepository) {
        this.usuarioRepository = usuarioRepository;
        this.gestanteRepository = gestanteRepository;
        this.medicoRepository = medicoRepository;
        this.credencialService = credencialService;
        this.passwordEncoder = passwordEncoder;
        this.dadosSocioeconomicosRepository = dadosSocioeconomicosRepository;
    }

    @Transactional
    public CredenciaisTemporariasResponse preCadastrar(PreCadastroGestanteRequest dados, Long idMedico) {
        if (usuarioRepository.existsByCpf(dados.cpf())) {
            throw new ConflitoException("Gestante já cadastrada");
        }

        String senhaAleatoria = credencialService.gerarSenhaAleatoria();
        CredenciaisTemporariasResponse credenciais = new CredenciaisTemporariasResponse(
                dados.cpf(),
                senhaAleatoria
        );

        gestanteRepository.save(new Gestante(
                dados.nome(),
                dados.cpf(),
                dados.email(),
                credenciais.login(),
                passwordEncoder.encode(credenciais.senha()),
                medicoRepository.getReferenceById(idMedico),
                dados.dataNascimento(),
                dados.telefone(),
                dados.dum()
        ));

        return credenciais;
    }

    // shortcut: devolve a lista inteira; paginar quando um médico passar a acompanhar centenas de gestantes
    @Transactional(readOnly = true)
    public List<GestanteResumoResponse> listarDoMedico(Long idMedico) {
        return gestanteRepository.findByMedicoIdOrderByNomeAsc(idMedico).stream()
                .map(g -> new GestanteResumoResponse(g.getId(), g.getNome(), g.getCpf(), g.getTelefone(),
                        g.getDum(), g.getDataProvavelParto(), g.primeiroAcessoPendente()))
                .toList();
    }

    @Transactional
    public void concluirPrimeiroAcesso(Long idGestante, PrimeiroAcessoRequest dados) {
        Gestante gestante = gestanteRepository.findById(idGestante).orElseThrow();
        if (!gestante.primeiroAcessoPendente()) {
            throw new ConflitoException("Primeiro acesso já concluído");
        }
        if (!dados.novoLogin().equals(gestante.getLogin()) && usuarioRepository.existsByLogin(dados.novoLogin())) {
            throw new ConflitoException("Login indisponível");
        }
        gestante.concluirPrimeiroAcesso(dados.novoLogin(), passwordEncoder.encode(dados.novaSenha()));
        gestante.registrarConsentimentoLgpd();
        dadosSocioeconomicosRepository.save(new DadosSocioeconomicos(idGestante, dados.rendaFamiliar(),
                dados.pessoasDomicilio(), dados.escolaridade(), dados.beneficiariaProgramaSocial()));
    }

    @Transactional
    public void concluirTutorial(Long idGestante) {
        gestanteRepository.findById(idGestante).orElseThrow().concluirTutorial();
    }
}
