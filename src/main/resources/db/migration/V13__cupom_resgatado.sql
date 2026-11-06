-- Cupom trocado pela gestante. O código é o que ela mostra na loja; a empresa o valida e ele passa a UTILIZADO.
-- Sem quantidade no cupom: a mesma gestante pode resgatar o mesmo cupom mais de uma vez, enquanto tiver saldo.
CREATE TABLE cupom_resgatado (
    id            BIGSERIAL    PRIMARY KEY,
    cupom_id      BIGINT       NOT NULL REFERENCES cupom (id),
    gestante_id   BIGINT       NOT NULL REFERENCES gestante (usuario_id),
    codigo        VARCHAR(12)  NOT NULL UNIQUE,
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('ATIVO', 'UTILIZADO', 'EXPIRADO')),
    data_resgate  TIMESTAMP    NOT NULL,
    data_uso      TIMESTAMP
);
CREATE INDEX ON cupom_resgatado (gestante_id);
CREATE INDEX ON cupom_resgatado (cupom_id);

-- O débito aponta para o cupom resgatado, como o crédito aponta para o agendamento; UNIQUE: um débito por resgate
ALTER TABLE transacao_pontos ADD COLUMN cupom_resgatado_id BIGINT UNIQUE REFERENCES cupom_resgatado (id);
