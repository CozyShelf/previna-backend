CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE usuario (
    id            BIGSERIAL    PRIMARY KEY,
    nome          VARCHAR(150) NOT NULL,
    cpf           VARCHAR(11)  NOT NULL UNIQUE,
    email         VARCHAR(150) UNIQUE,
    login         VARCHAR(50)  NOT NULL UNIQUE,
    senha_hash    VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL,
    status_conta  VARCHAR(30)  NOT NULL,
    criado_em     TIMESTAMP    NOT NULL
);

CREATE TABLE medico (
    usuario_id    BIGINT       PRIMARY KEY REFERENCES usuario (id),
    crm           VARCHAR(20)  NOT NULL UNIQUE
);

CREATE TABLE gestante (
    usuario_id           BIGINT      PRIMARY KEY REFERENCES usuario (id),
    medico_id            BIGINT      NOT NULL REFERENCES medico (usuario_id),
    data_nascimento      DATE        NOT NULL,
    telefone             VARCHAR(11) NOT NULL,
    dum                  DATE        NOT NULL,
    data_provavel_parto  DATE        NOT NULL,
    saldo_pontos         INTEGER     NOT NULL DEFAULT 0,
    consentimento_lgpd   BOOLEAN     NOT NULL DEFAULT FALSE,
    data_consentimento   TIMESTAMP,
    tutorial_concluido   BOOLEAN     NOT NULL DEFAULT FALSE
);
