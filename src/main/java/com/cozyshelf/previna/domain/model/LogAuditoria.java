package com.cozyshelf.previna.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

// Registro do log de auditoria (RF18): quem fez o quê, quando e sobre qual gestante, gravado na mesma transação da
// operação (ver Auditoria). Só é inserido: @Immutable faz o Hibernate nunca gerar UPDATE, e o gatilho da V24 barra
// UPDATE e DELETE no banco. As referências são só os ids: o log é escrito e não navegado.
// O registro é montado pelos métodos de fábrica, um por ação, para cada ação preencher só as colunas dela.
@Entity
@Immutable
@Table(name = "log_auditoria")
public class LogAuditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    private AcaoAuditoria acao;

    // Quem fez: o médico, a gestante ou a empresa
    private Long usuarioId;
    private Long gestanteId;
    private Long agendamentoId;
    // O crédito ou o débito, quando houver
    private Long transacaoPontosId;
    private Long cupomResgatadoId;
    // Cópia dos pontos da transação, para ler o log sem juntar tabelas
    private Integer pontos;

    protected LogAuditoria() {
    }

    private LogAuditoria(LocalDateTime dataHora, AcaoAuditoria acao, Long usuarioId, Long gestanteId) {
        this.dataHora = dataHora;
        this.acao = acao;
        this.usuarioId = usuarioId;
        this.gestanteId = gestanteId;
    }

    // Agendamento criado, realizado ou não comparecido pelo médico. No realizado, credito é o crédito de pontos;
    // nos outros, nulo
    public static LogAuditoria deAgendamento(AcaoAuditoria acao, LocalDateTime dataHora, Long idMedico,
                                             Agendamento agendamento, TransacaoPontos credito) {
        LogAuditoria log = new LogAuditoria(dataHora, acao, idMedico, agendamento.getGestante().getId());
        log.agendamentoId = agendamento.getId();
        log.comTransacao(credito);
        return log;
    }

    // Cupom (com o débito) ou promoção (sem débito) resgatados pela própria gestante, na data do resgate
    public static LogAuditoria deResgate(AcaoAuditoria acao, CupomResgatado resgate, TransacaoPontos debito) {
        Long idGestante = resgate.getGestante().getId();
        LogAuditoria log = new LogAuditoria(resgate.getDataResgate(), acao, idGestante, idGestante);
        log.cupomResgatadoId = resgate.getId();
        log.comTransacao(debito);
        return log;
    }

    // Código validado pela empresa na loja, na data de uso gravada no resgate
    public static LogAuditoria deValidacao(LocalDateTime dataHora, Long idEmpresa, CupomResgatado resgate) {
        LogAuditoria log = new LogAuditoria(dataHora, AcaoAuditoria.RESGATE_VALIDADO, idEmpresa,
                resgate.getGestante().getId());
        log.cupomResgatadoId = resgate.getId();
        return log;
    }

    private void comTransacao(TransacaoPontos transacao) {
        if (transacao != null) {
            this.transacaoPontosId = transacao.getId();
            this.pontos = transacao.getPontos();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public AcaoAuditoria getAcao() {
        return acao;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getGestanteId() {
        return gestanteId;
    }

    public Long getAgendamentoId() {
        return agendamentoId;
    }

    public Long getTransacaoPontosId() {
        return transacaoPontosId;
    }

    public Long getCupomResgatadoId() {
        return cupomResgatadoId;
    }

    public Integer getPontos() {
        return pontos;
    }
}
