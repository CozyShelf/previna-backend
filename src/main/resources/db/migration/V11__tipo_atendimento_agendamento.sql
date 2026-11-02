-- Exames e consultas do pré-natal: o catálogo (tipo_atendimento), o que foi marcado para cada gestante
-- (agendamento) e o extrato de pontos que a confirmação gera (transacao_pontos).

-- Dado de referência, e não seed: db/seed só carrega no profile dev, e o catálogo precisa existir em produção
CREATE TABLE tipo_atendimento (
    id           BIGSERIAL    PRIMARY KEY,
    nome         VARCHAR(120) NOT NULL UNIQUE,
    categoria    VARCHAR(20)  NOT NULL CHECK (categoria IN ('EXAME', 'CONSULTA')),
    descricao    TEXT,
    pontos_base  INTEGER      NOT NULL CHECK (pontos_base > 0),
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE agendamento (
    id                   BIGSERIAL   PRIMARY KEY,
    gestante_id          BIGINT      NOT NULL REFERENCES gestante (usuario_id),
    tipo_atendimento_id  BIGINT      NOT NULL REFERENCES tipo_atendimento (id),
    atribuido_por        BIGINT      NOT NULL REFERENCES medico (usuario_id),
    confirmado_por       BIGINT      REFERENCES medico (usuario_id),
    data_prevista        DATE        NOT NULL,
    status               VARCHAR(20) NOT NULL CHECK (status IN ('AGENDADO', 'REALIZADO', 'NAO_COMPARECEU')),
    data_confirmacao     TIMESTAMP,
    observacao           TEXT,
    -- Pendente nunca tem desfecho; desfecho nunca fica sem data e sem quem registrou
    CHECK ((status = 'AGENDADO') = (data_confirmacao IS NULL AND confirmado_por IS NULL))
);
CREATE INDEX ON agendamento (gestante_id);

CREATE TABLE transacao_pontos (
    id              BIGSERIAL     PRIMARY KEY,
    gestante_id     BIGINT        NOT NULL REFERENCES gestante (usuario_id),
    -- UNIQUE: duas confirmações simultâneas do mesmo agendamento não creditam duas vezes
    agendamento_id  BIGINT        UNIQUE REFERENCES agendamento (id),
    tipo            VARCHAR(10)   NOT NULL CHECK (tipo IN ('CREDITO', 'DEBITO')),
    pontos          INTEGER       NOT NULL CHECK (pontos > 0),
    fator_aplicado  NUMERIC(4, 2),
    data_hora       TIMESTAMP     NOT NULL
);
CREATE INDEX ON transacao_pontos (gestante_id);

-- Exames e consultas comuns no pré-natal; pontos_base maior na consulta, que exige a ida ao serviço
INSERT INTO tipo_atendimento (nome, categoria, pontos_base) VALUES
    ('Consulta de pré-natal — 1º trimestre', 'CONSULTA', 50),
    ('Consulta de pré-natal — 2º trimestre', 'CONSULTA', 50),
    ('Consulta de pré-natal — 3º trimestre', 'CONSULTA', 50),
    ('Consulta odontológica', 'CONSULTA', 40),
    ('Consulta puerperal', 'CONSULTA', 50),
    ('Hemograma completo', 'EXAME', 25),
    ('Tipagem sanguínea e fator Rh', 'EXAME', 25),
    ('Coombs indireto', 'EXAME', 25),
    ('Glicemia de jejum', 'EXAME', 25),
    ('Teste de tolerância à glicose (TOTG 75 g)', 'EXAME', 30),
    ('Sorologia para sífilis (VDRL)', 'EXAME', 25),
    ('Teste rápido de sífilis', 'EXAME', 20),
    ('Sorologia para HIV', 'EXAME', 25),
    ('Teste rápido de HIV', 'EXAME', 20),
    ('Sorologia para hepatite B (HBsAg)', 'EXAME', 25),
    ('Sorologia para hepatite C (anti-HCV)', 'EXAME', 25),
    ('Sorologia para toxoplasmose (IgG e IgM)', 'EXAME', 25),
    ('Urina tipo I (EAS)', 'EXAME', 20),
    ('Urocultura com antibiograma', 'EXAME', 20),
    ('Ultrassonografia obstétrica', 'EXAME', 35),
    ('Ultrassonografia morfológica', 'EXAME', 35);
