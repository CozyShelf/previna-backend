package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.MedicoRequest;
import com.cozyshelf.previna.application.dto.response.MedicoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.DadosInvalidosException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.Medico;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.MedicoRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class MedicoService {
    private final UsuarioRepository usuarioRepository;
    private final MedicoRepository medicoRepository;
    private final PasswordEncoder passwordEncoder;

    public MedicoService(UsuarioRepository usuarioRepository, MedicoRepository medicoRepository,
                         PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.medicoRepository = medicoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MedicoResponse cadastrar(MedicoRequest dados) {
        if (dados.senha() == null) {
            throw new DadosInvalidosException("Informe a senha do médico");
        }
        verificarDisponibilidade(dados, null);

        Medico medico = medicoRepository.save(new Medico(dados.nome(), dados.cpf(), dados.email(), dados.crm(),
                dados.login(), passwordEncoder.encode(dados.senha()), dados.verTodasGestantes()));
        return resposta(medico);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<MedicoResponse> listar(ListagemRequest listagem, Boolean verTodasGestantes) {
        Specification<Medico> filtro = Specification.allOf(
                Filtros.busca(listagem.busca(), "cpf", "nome", "crm", "email", "login"),
                Filtros.igual("verTodasGestantes", verTodasGestantes));
        return PaginaResponse.de(medicoRepository.findAll(filtro, listagem.ordenadaPor("nome"))
                .map(MedicoService::resposta));
    }

    @Transactional(readOnly = true)
    public MedicoResponse buscar(Long id) {
        return resposta(encontrar(id));
    }

    @Transactional
    public MedicoResponse atualizar(Long id, MedicoRequest dados) {
        Medico medico = encontrar(id);
        verificarDisponibilidade(dados, medico);

        medico.atualizar(dados.nome(), dados.cpf(), dados.email(), dados.crm(), dados.login(),
                dados.verTodasGestantes());
        if (dados.senha() != null) {
            medico.alterarSenha(passwordEncoder.encode(dados.senha()));
        }
        return resposta(medico);
    }

    private Medico encontrar(Long id) {
        return medicoRepository.findById(id).orElseThrow(() -> new NaoEncontradoException("Médico não encontrado"));
    }

    // Na edição só confere o que mudou: os valores atuais já são do próprio médico
    private void verificarDisponibilidade(MedicoRequest dados, Medico atual) {
        if (emUso(dados.cpf(), atual, Medico::getCpf, usuarioRepository::existsByCpf)) {
            throw new ConflitoException("CPF já cadastrado");
        }
        if (emUso(dados.crm(), atual, Medico::getCrm, medicoRepository::existsByCrm)) {
            throw new ConflitoException("CRM já cadastrado");
        }
        if (emUso(dados.email(), atual, Medico::getEmail, usuarioRepository::existsByEmail)) {
            throw new ConflitoException("E-mail já cadastrado");
        }
        if (emUso(dados.login(), atual, Medico::getLogin, usuarioRepository::existsByLogin)) {
            throw new ConflitoException("Login indisponível");
        }
    }

    private static boolean emUso(String valor, Medico atual, Function<Medico, String> valorAtual,
                                 Predicate<String> existe) {
        return (atual == null || !valor.equals(valorAtual.apply(atual))) && existe.test(valor);
    }

    private static MedicoResponse resposta(Medico medico) {
        return new MedicoResponse(medico.getId(), medico.getNome(), medico.getCpf(), medico.getEmail(),
                medico.getCrm(), medico.getLogin(), medico.podeVerTodasGestantes());
    }
}
