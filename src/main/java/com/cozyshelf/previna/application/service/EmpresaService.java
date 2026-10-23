package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.AtualizacaoPerfilEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.ListagemRequest;
import com.cozyshelf.previna.application.dto.request.PreCadastroEmpresaRequest;
import com.cozyshelf.previna.application.dto.request.PrimeiroAcessoEmpresaRequest;
import com.cozyshelf.previna.application.dto.response.EmpresaResumoResponse;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.PerfilEmpresaResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.model.LogoEmpresa;
import com.cozyshelf.previna.domain.model.StatusConta;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.domain.repository.Filtros;
import com.cozyshelf.previna.domain.repository.LogoEmpresaRepository;
import com.cozyshelf.previna.domain.repository.UsuarioRepository;
import com.cozyshelf.previna.infrastructure.email.EmailService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class EmpresaService {
    private final UsuarioRepository usuarioRepository;
    private final EmpresaParceiraRepository empresaRepository;
    private final LogoEmpresaRepository logoRepository;
    private final CredencialService credencialService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public EmpresaService(UsuarioRepository usuarioRepository, EmpresaParceiraRepository empresaRepository,
                          LogoEmpresaRepository logoRepository, CredencialService credencialService,
                          PasswordEncoder passwordEncoder, EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.logoRepository = logoRepository;
        this.credencialService = credencialService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public EmpresaResumoResponse preCadastrar(PreCadastroEmpresaRequest dados) {
        if (empresaRepository.existsByCnpj(dados.cnpj())) {
            throw new ConflitoException("Empresa já cadastrada");
        }

        // O CNPJ é o login temporário, como o CPF da gestante
        String senhaTemporaria = credencialService.gerarSenhaAleatoria();
        EmpresaParceira empresa = empresaRepository.save(new EmpresaParceira(
                dados.razaoSocial(),
                dados.cnpj(),
                dados.email(),
                dados.telefone(),
                dados.responsavel(),
                dados.cnpj(),
                passwordEncoder.encode(senhaTemporaria)
        ));

        // Dentro da transação: se o e-mail falhar, o pré-cadastro é desfeito e o administrador tenta de novo
        emailService.enviarPreLogin(dados.email(), dados.cnpj(), senhaTemporaria);
        return resumo(empresa);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<EmpresaResumoResponse> listar(ListagemRequest listagem, StatusConta situacao) {
        Specification<EmpresaParceira> filtro = Specification.allOf(
                Filtros.busca(listagem.busca(), "cnpj", "razaoSocial", "responsavel", "email"),
                Filtros.igual("statusConta", situacao));
        return PaginaResponse.de(empresaRepository.findAll(filtro, listagem.ordenadaPor("razaoSocial"))
                .map(EmpresaService::resumo));
    }

    @Transactional
    public void concluirPrimeiroAcesso(Long idEmpresa, PrimeiroAcessoEmpresaRequest dados, MultipartFile logo) {
        EmpresaParceira empresa = empresaRepository.findById(idEmpresa).orElseThrow();
        if (!empresa.primeiroAcessoPendente()) {
            throw new ConflitoException("Primeiro acesso já concluído");
        }
        if (!dados.novoLogin().equals(empresa.getLogin()) && usuarioRepository.existsByLogin(dados.novoLogin())) {
            throw new ConflitoException("Login indisponível");
        }
        // Valida a imagem antes de mexer na conta
        Optional<Imagem> imagem = Imagem.de(logo);

        empresa.concluirPrimeiroAcesso(dados.novoLogin(), passwordEncoder.encode(dados.novaSenha()));
        empresa.completarCadastro(dados.endereco(), dados.descricao(), dados.segmento());
        imagem.ifPresent(i -> salvarLogo(idEmpresa, empresa, i));
    }

    @Transactional(readOnly = true)
    public PerfilEmpresaResponse buscarPerfil(Long idEmpresa) {
        return perfil(empresaRepository.findById(idEmpresa).orElseThrow());
    }

    @Transactional
    public PerfilEmpresaResponse atualizarPerfil(Long idEmpresa, AtualizacaoPerfilEmpresaRequest dados,
                                                 MultipartFile logo) {
        EmpresaParceira empresa = empresaRepository.findById(idEmpresa).orElseThrow();
        if (!dados.email().equals(empresa.getEmail()) && usuarioRepository.existsByEmail(dados.email())) {
            throw new ConflitoException("E-mail já cadastrado");
        }
        Optional<Imagem> imagem = Imagem.de(logo);

        empresa.atualizarContato(dados.email(), dados.telefone(), dados.responsavel());
        empresa.completarCadastro(dados.endereco(), dados.descricao(), dados.segmento());
        if (imagem.isPresent()) {
            salvarLogo(idEmpresa, empresa, imagem.get());
        } else if (Boolean.TRUE.equals(dados.removerLogo())) {
            logoRepository.deleteById(idEmpresa);
            empresa.alterarLogo(null);
        }
        return perfil(empresa);
    }

    @Transactional(readOnly = true)
    public Optional<LogoEmpresa> buscarLogo(Long idEmpresa) {
        return logoRepository.findById(idEmpresa);
    }

    private void salvarLogo(Long idEmpresa, EmpresaParceira empresa, Imagem imagem) {
        logoRepository.save(new LogoEmpresa(idEmpresa, imagem.conteudo(), imagem.tipo()));
        // A versão no endereço faz o navegador buscar o logo novo em vez do que já exibiu
        empresa.alterarLogo("/empresas/%d/logo?v=%d".formatted(idEmpresa, System.currentTimeMillis()));
    }

    private static PerfilEmpresaResponse perfil(EmpresaParceira empresa) {
        return new PerfilEmpresaResponse(empresa.getRazaoSocial(), empresa.getCnpj(), empresa.getEmail(),
                empresa.getTelefone(), empresa.getResponsavel(), empresa.getEndereco(), empresa.getSegmento(),
                empresa.getDescricao(), empresa.getLogoUrl());
    }

    private static EmpresaResumoResponse resumo(EmpresaParceira empresa) {
        return new EmpresaResumoResponse(empresa.getId(), empresa.getRazaoSocial(), empresa.getCnpj(),
                empresa.getEmail(), empresa.getTelefone(), empresa.getResponsavel(), empresa.getLogoUrl(),
                empresa.primeiroAcessoPendente());
    }
}
