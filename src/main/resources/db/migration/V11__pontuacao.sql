-- Consultas e exames do pré-natal; pontos_base é a pontuação antes do fator socioeconômico
CREATE TABLE tipo_atendimento (
    id           BIGSERIAL     PRIMARY KEY,
    nome         VARCHAR(100)  NOT NULL,
    categoria    VARCHAR(20)   NOT NULL CHECK (categoria IN ('EXAME', 'CONSULTA')),
    descricao    TEXT,
    pontos_base  INTEGER       NOT NULL CHECK (pontos_base > 0),
    ativo        BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE agendamento (
    id                   BIGSERIAL    PRIMARY KEY,
    gestante_id          BIGINT       NOT NULL REFERENCES gestante (usuario_id),
    tipo_atendimento_id  BIGINT       NOT NULL REFERENCES tipo_atendimento (id),
    atribuido_por        BIGINT       NOT NULL REFERENCES medico (usuario_id),
    confirmado_por       BIGINT       REFERENCES medico (usuario_id),
    data_prevista        DATE         NOT NULL,
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('AGENDADO', 'REALIZADO', 'CANCELADO')),
    data_confirmacao     TIMESTAMP,
    observacao           TEXT
);
CREATE INDEX ON agendamento (gestante_id);

-- Crédito vem da confirmação de um agendamento (no máximo um por agendamento); débito, do resgate de cupom,
-- e cupom_resgatado_id entra com a migração do resgate
CREATE TABLE transacao_pontos (
    id              BIGSERIAL      PRIMARY KEY,
    gestante_id     BIGINT         NOT NULL REFERENCES gestante (usuario_id),
    agendamento_id  BIGINT         UNIQUE REFERENCES agendamento (id),
    tipo            VARCHAR(10)    NOT NULL CHECK (tipo IN ('CREDITO', 'DEBITO')),
    pontos          INTEGER        NOT NULL CHECK (pontos > 0),
    fator_aplicado  NUMERIC(4, 2),
    data_hora       TIMESTAMP      NOT NULL
);
CREATE INDEX ON transacao_pontos (gestante_id, data_hora);
