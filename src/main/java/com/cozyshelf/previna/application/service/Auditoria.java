package com.cozyshelf.previna.application.service;

import com.cozyshelf.previna.domain.model.AcaoAuditoria;
import com.cozyshelf.previna.domain.model.Agendamento;
import com.cozyshelf.previna.domain.model.CupomResgatado;
import com.cozyshelf.previna.domain.model.LogAuditoria;
import com.cozyshelf.previna.domain.model.StatusAgendamento;
import com.cozyshelf.previna.domain.model.TransacaoPontos;
import com.cozyshelf.previna.domain.repository.LogAuditoriaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Log de auditoria (RF18): os serviços chamam estes métodos explicitamente, depois de gravar a operação.
// MANDATORY: sem uma transação aberta por quem chama, a chamada falha em vez de abrir uma própria. O registro vai
// na mesma transação da operação: se a gravação do log falhar, a operação inteira é desfeita, e um log nunca fica
// gravado sem a operação. A hora é a da própria operação (a da JVM, ver PrevinaApplication), e não o now() do banco.
// Só os caminhos que dão certo chamam: erros (404, 409, 400) e a repetição idempotente do resgate não geram registro.
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class Auditoria {
    private final LogAuditoriaRepository repository;

    public Auditoria(LogAuditoriaRepository repository) {
        this.repository = repository;
    }

    // Uma linha por agendamento do cadastro, todas com a mesma hora
    public void agendamentosCriados(Long idMedico, List<Agendamento> agendamentos) {
        LocalDateTime agora = LocalDateTime.now();
        repository.saveAll(agendamentos.stream()
                .map(a -> LogAuditoria.deAgendamento(AcaoAuditoria.AGENDAMENTO_CRIADO, agora, idMedico, a, null))
                .toList());
    }

    // Desfecho registrado pelo médico, na hora gravada na confirmação. credito: o crédito do realizado; nulo no não
    // comparecimento
    public void agendamentoResolvido(Long idMedico, Agendamento agendamento, TransacaoPontos credito) {
        AcaoAuditoria acao = agendamento.getStatus() == StatusAgendamento.REALIZADO
                ? AcaoAuditoria.AGENDAMENTO_REALIZADO
                : AcaoAuditoria.AGENDAMENTO_NAO_COMPARECEU;
        repository.save(LogAuditoria.deAgendamento(acao, agendamento.getDataConfirmacao(), idMedico, agendamento,
                credito));
    }

    // Cupom resgatado pela gestante, com o débito
    public void cupomResgatado(CupomResgatado resgate, TransacaoPontos debito) {
        repository.save(LogAuditoria.deResgate(AcaoAuditoria.CUPOM_RESGATADO, resgate, debito));
    }

    // Promoção resgatada pela gestante: sem transação nem pontos
    public void promocaoResgatada(CupomResgatado resgate) {
        repository.save(LogAuditoria.deResgate(AcaoAuditoria.PROMOCAO_RESGATADA, resgate, null));
    }

    // Código validado pela empresa; quando é a data de uso gravada no resgate
    public void resgateValidado(Long idEmpresa, CupomResgatado resgate, LocalDateTime quando) {
        repository.save(LogAuditoria.deValidacao(quando, idEmpresa, resgate));
    }
}
