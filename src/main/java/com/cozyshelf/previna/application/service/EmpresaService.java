package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.PreCadastroEmpresaRequest;
import com.cozyshelf.previna.application.dto.response.EmpresaResumoResponse;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.domain.model.EmpresaParceira;
import com.cozyshelf.previna.domain.repository.EmpresaParceiraRepository;
import com.cozyshelf.previna.infrastructure.email.EmailService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmpresaService {
    private final EmpresaParceiraRepository empresaRepository;
    private final CredencialService credencialService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public EmpresaService(EmpresaParceiraRepository empresaRepository, CredencialService credencialService,
                          PasswordEncoder passwordEncoder, EmailService emailService) {
        this.empresaRepository = empresaRepository;
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

    // shortcut: devolve a lista inteira; paginar quando houver centenas de empresas parceiras
    @Transactional(readOnly = true)
    public List<EmpresaResumoResponse> listar() {
        return empresaRepository.findAllByOrderByRazaoSocialAsc().stream()
                .map(EmpresaService::resumo)
                .toList();
    }

    private static EmpresaResumoResponse resumo(EmpresaParceira empresa) {
        return new EmpresaResumoResponse(empresa.getId(), empresa.getRazaoSocial(), empresa.getCnpj(),
                empresa.getEmail(), empresa.getTelefone(), empresa.getResponsavel(),
                empresa.primeiroAcessoPendente());
    }
}
