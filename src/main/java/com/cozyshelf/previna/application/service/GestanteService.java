package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilGestanteRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PreCadastroGestanteRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoRequest;
import com.cozyshelf.previna.application.dto.response.CredenciaisTemporariasResponse;
import com.cozyshelf.previna.application.dto.response.GestanteResumoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PerfilGestanteResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.DadosSocioeconomicos;
import com.cozyshelf.previna.domain.model.FotoGestante;
import com.cozyshelf.previna.domain.model.Gestante;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.repository.DadosSocioeconomicosRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.FotoGestanteRepository;
import com.cozyshelf.previna.domain.repository.GestanteRepository;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class GestanteService {
    private final UsuarioRepository usuarioRepository;
    private final GestanteRepository gestanteRepository;
    private final MedicoRepository medicoRepository;
    private final CredencialService credencialService;
    private final PasswordEncoder passwordEncoder;
    private final DadosSocioeconomicosRepository dadosSocioeconomicosRepository;
    private final FotoGestanteRepository fotoRepository;

    public GestanteService(UsuarioRepository usuarioRepository, GestanteRepository gestanteRepository,
                           MedicoRepository medicoRepository, CredencialService credencialService,
                           PasswordEncoder passwordEncoder, DadosSocioeconomicosRepository dadosSocioeconomicosRepository,
                           FotoGestanteRepository fotoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.gestanteRepository = gestanteRepository;
        this.medicoRepository = medicoRepository;
        this.credencialService = credencialService;
        this.passwordEncoder = passwordEncoder;
        this.dadosSocioeconomicosRepository = dadosSocioeconomicosRepository;
        this.fotoRepository = fotoRepository;
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

    @Transactional(readOnly = true)
    public PaginaResponse<GestanteResumoResponse> listar(Long idMedico, ListagemRequest listagem,
                                                         StatusConta situacao, boolean somenteMinhas) {
        Medico medico = medicoRepository.findById(idMedico).orElseThrow();
        // Sem a permissão dada pelo administrador, o médico só vê as gestantes que acompanha
        boolean todas = medico.podeVerTodasGestantes() && !somenteMinhas;
        Specification<Gestante> filtro = Specification.allOf(
                Filtros.busca(listagem.busca(), "cpf", "nome"),
                Filtros.igual("statusConta", situacao),
                Filtros.igual("medico", todas ? null : medico));
        return PaginaResponse.de(gestanteRepository.findAll(filtro, listagem.ordenadaPor("nome"))
                .map(g -> new GestanteResumoResponse(g.getId(), g.getNome(), g.getCpf(), g.getTelefone(),
                        g.getDum(), g.getDataProvavelParto(), g.primeiroAcessoPendente(),
                        g.getMedico().getNome())));
    }

    @Transactional
    public void concluirPrimeiroAcesso(Long idGestante, PrimeiroAcessoRequest dados, MultipartFile foto) {
        Gestante gestante = gestanteRepository.findById(idGestante).orElseThrow();
        if (!gestante.primeiroAcessoPendente()) {
            throw new ConflitoException("Primeiro acesso já concluído");
        }
        if (!dados.novoLogin().equals(gestante.getLogin()) && usuarioRepository.existsByLogin(dados.novoLogin())) {
            throw new ConflitoException("Login indisponível");
        }
        // Valida a imagem antes de mexer na conta
        Optional<Imagem> imagem = Imagem.de(foto);

        gestante.concluirPrimeiroAcesso(dados.novoLogin(), passwordEncoder.encode(dados.novaSenha()));
        gestante.registrarConsentimentoLgpd();
        dadosSocioeconomicosRepository.save(new DadosSocioeconomicos(idGestante, dados.rendaFamiliar(),
                dados.pessoasDomicilio(), dados.escolaridade(), dados.beneficiariaProgramaSocial()));
        imagem.ifPresent(i -> fotoRepository.save(new FotoGestante(idGestante, i.conteudo(), i.tipo())));
    }

    @Transactional(readOnly = true)
    public PerfilGestanteResponse buscarPerfil(Long idGestante) {
        return perfil(idGestante, gestanteRepository.findById(idGestante).orElseThrow());
    }

    @Transactional
    public PerfilGestanteResponse atualizarPerfil(Long idGestante, AtualizacaoPerfilGestanteRequest dados,
                                                  MultipartFile foto) {
        Gestante gestante = gestanteRepository.findById(idGestante).orElseThrow();
        String email = dados.emailOuNulo();
        if (email != null && !email.equals(gestante.getEmail()) && usuarioRepository.existsByEmail(email)) {
            throw new ConflitoException("E-mail já cadastrado");
        }
        Optional<Imagem> imagem = Imagem.de(foto);

        gestante.atualizarContato(dados.telefone(), email);
        if (imagem.isPresent()) {
            fotoRepository.save(new FotoGestante(idGestante, imagem.get().conteudo(), imagem.get().tipo()));
        } else if (Boolean.TRUE.equals(dados.removerFoto())) {
            fotoRepository.deleteById(idGestante);
        }
        return perfil(idGestante, gestante);
    }

    @Transactional(readOnly = true)
    public Optional<FotoGestante> buscarFoto(Long idGestante) {
        return fotoRepository.findById(idGestante);
    }

    private PerfilGestanteResponse perfil(Long idGestante, Gestante gestante) {
        return new PerfilGestanteResponse(gestante.getNome(), gestante.getEmail(), gestante.getTelefone(),
                fotoRepository.existsById(idGestante));
    }

    @Transactional
    public void concluirTutorial(Long idGestante) {
        gestanteRepository.findById(idGestante).orElseThrow().concluirTutorial();
    }
}
