package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.application.dto.request.ResgateRequest;
import com.cozyshelf.previna.application.dto.response.PaginaResponse;
import com.cozyshelf.previna.application.dto.response.ResgateResponse;
import com.cozyshelf.previna.application.dto.response.ValidacaoCupomResponse;
import com.cozyshelf.previna.application.exception.CodigoErro;
import com.cozyshelf.previna.application.exception.ConflitoException;
import com.cozyshelf.previna.application.exception.NaoEncontradoException;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.repository.CupomResgatadoRepository;
import com.cozyshelf.previna.domain.repository.ResgateDaEmpresa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

// Os cupons e as promoções resgatados: "meus cupons" da gestante, com o código para mostrar na loja, e a validação
// desse código pela empresa.
// O status EXPIRADO é gravado na leitura: antes de listar (ou detalhar), os ATIVOS vencidos viram EXPIRADO com um
// UPDATE condicionado a ATIVO (ver CupomResgatadoRepository.EXPIRAR). Por isso as transações não são readOnly.
@Service
public class ResgateService {
    static final String RESGATE_NAO_ENCONTRADO = "Resgate não encontrado";
    static final String CODIGO_NAO_ENCONTRADO = "Código não encontrado";
    static final String CODIGO_JA_UTILIZADO = "Código já utilizado";
    static final String CODIGO_EXPIRADO = "Código expirado";

    private final CupomResgatadoRepository resgatadoRepository;

    public ResgateService(CupomResgatadoRepository resgatadoRepository) {
        this.resgatadoRepository = resgatadoRepository;
    }

    // ATIVO primeiro, depois UTILIZADO e EXPIRADO juntos, cada grupo do resgate mais recente para o mais antigo
    @Transactional
    public PaginaResponse<ResgateResponse> listar(Long idGestante, ResgateRequest filtros) {
        resgatadoRepository.expirarVencidos(idGestante, LocalDate.now());
        return PaginaResponse.de(resgatadoRepository.daGestante(idGestante, filtros.tipo(), filtros.status(),
                filtros.categoria(), filtros.padraoBusca(), filtros.paginacao()).map(ResgateResponse::de));
    }

    // Resgate de outra gestante responde como inexistente, para não revelar os ids dos resgates dos outros
    @Transactional
    public ResgateResponse detalhar(Long idGestante, Long idResgate) {
        resgatadoRepository.expirarSeVencido(idResgate, idGestante, LocalDate.now());
        return resgatadoRepository.daGestante(idResgate, idGestante).map(ResgateResponse::de)
                .orElseThrow(() -> new NaoEncontradoException(RESGATE_NAO_ENCONTRADO));
    }

    // Validação do código na loja, para cupom e promoção. Quem decide é o UPDATE atômico do repositório
    // (utilizar: ATIVO e dentro da validade vira UTILIZADO), e não uma leitura antes: duas validações simultâneas do
    // mesmo código nunca passam as duas. Só quando nada mudou o resgate é relido, para dizer por quê.
    // Vale a cópia gravada no resgate (D2): cupom ou promoção desativados ou editados depois não invalidam o código.
    // O código de outra empresa responde igual ao inexistente, para não revelar os códigos das outras.
    // noRollbackFor: o 409 do vencido sai por exceção, e o EXPIRADO gravado antes dela tem de ser comitado (sem isto
    // o rollback o desfazia). Nos outros 409 e no 404 o UPDATE não mudou nada, então não há o que desfazer
    @Transactional(noRollbackFor = ConflitoException.class)
    public ValidacaoCupomResponse validar(Long idEmpresa, String codigoDigitado) {
        String codigo = normalizar(codigoDigitado);
        // A mesma hora da JVM (America/Sao_Paulo, ver PrevinaApplication) da data do resgate e da expiração na leitura
        LocalDateTime agora = LocalDateTime.now();
        LocalDate hoje = agora.toLocalDate();
        // Uma segunda volta só se o resgate apareceu ATIVO e válido depois do UPDATE que não o achou (comitado entre
        // os dois comandos); os status só saem de ATIVO, nunca voltam, então a segunda volta sempre decide
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            if (resgatadoRepository.utilizar(codigo, idEmpresa, agora, hoje) == 1) {
                return resgatadoRepository.daEmpresa(codigo, idEmpresa).map(ValidacaoCupomResponse::de)
                        .orElseThrow();
            }
            CupomResgatado resgate = resgatadoRepository.daEmpresa(codigo, idEmpresa)
                    .map(ResgateDaEmpresa::resgate)
                    .orElseThrow(() -> new NaoEncontradoException(CODIGO_NAO_ENCONTRADO,
                            CodigoErro.RESGATE_NAO_ENCONTRADO));
            switch (resgate.getStatus()) {
                case UTILIZADO -> throw new ConflitoException(CODIGO_JA_UTILIZADO, CodigoErro.RESGATE_JA_UTILIZADO,
                        ResgateResponse.instante(resgate.getDataUso()));
                case EXPIRADO -> throw new ConflitoException(CODIGO_EXPIRADO, CodigoErro.RESGATE_EXPIRADO);
                case ATIVO -> {
                    if (resgate.getDataValidade().isBefore(hoje)) {
                        // Grava o EXPIRADO antes de responder. Condicionado a ATIVO: se a listagem da gestante o
                        // expirou no meio, nada muda, e a resposta é a mesma
                        resgatadoRepository.expirarCodigoSeVencido(codigo, idEmpresa, hoje);
                        throw new ConflitoException(CODIGO_EXPIRADO, CodigoErro.RESGATE_EXPIRADO);
                    }
                }
            }
        }
        throw new IllegalStateException("O resgate " + codigo + " continua ATIVO e válido, mas não foi validado");
    }

    // O código é gerado em maiúsculas (GeradorCodigoResgate): aceita o digitado em minúsculas e com espaços nas pontas
    static String normalizar(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }
}
