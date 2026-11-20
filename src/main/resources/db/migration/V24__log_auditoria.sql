-- Log de auditoria (RF18): registra quem fez o quê nas confirmações de atendimento e nas movimentações
-- de pontos, na mesma transação da operação. Só aceita INSERT: o gatilho barra UPDATE e DELETE, para
-- que o registro não possa ser alterado nem apagado pela aplicação.
CREATE TABLE log_auditoria (
    id                  BIGSERIAL    PRIMARY KEY,
    data_hora           TIMESTAMP    NOT NULL,
    acao                VARCHAR(30)  NOT NULL CHECK (acao IN (
                            'AGENDAMENTO_CRIADO',
                            'AGENDAMENTO_REALIZADO',
                            'AGENDAMENTO_NAO_COMPARECEU',
                            'CUPOM_RESGATADO',
                            'PROMOCAO_RESGATADA',
                            'RESGATE_VALIDADO')),
    usuario_id          BIGINT       NOT NULL REFERENCES usuario (id),         -- quem fez: médico, gestante ou empresa
    gestante_id         BIGINT       NOT NULL REFERENCES gestante (usuario_id),
    agendamento_id      BIGINT       REFERENCES agendamento (id),
    transacao_pontos_id BIGINT       REFERENCES transacao_pontos (id),         -- o crédito ou o débito, quando houver
    cupom_resgatado_id  BIGINT       REFERENCES cupom_resgatado (id),
    pontos              INTEGER                                                -- cópia, para ler o log sem juntar tabelas
);
CREATE INDEX ON log_auditoria (gestante_id, data_hora);
CREATE INDEX ON log_auditoria (usuario_id, data_hora);

CREATE FUNCTION log_auditoria_somente_insercao() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'log_auditoria não aceita %', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER log_auditoria_somente_insercao
    BEFORE UPDATE OR DELETE ON log_auditoria
    FOR EACH ROW EXECUTE FUNCTION log_auditoria_somente_insercao();
