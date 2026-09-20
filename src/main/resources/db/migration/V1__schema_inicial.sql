-- =====================================================================
-- Schema Temporario do Projeto.
-- Mudar quando a modelagem de entidades do projeto for definida
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE gestante (
    id                     UUID         PRIMARY KEY,
    nome_completo          VARCHAR(150) NOT NULL,
    cpf                    VARCHAR(11)  NOT NULL UNIQUE,
    email                  VARCHAR(150) NOT NULL UNIQUE,
    telefone               VARCHAR(11)  NOT NULL,
    data_nascimento        DATE         NOT NULL,
    primeira_gestacao      BOOLEAN      NOT NULL DEFAULT TRUE,
    senha                  VARCHAR(100) NOT NULL,
    ativa                  BOOLEAN      NOT NULL DEFAULT TRUE,
    cadastrada_em          TIMESTAMP    NOT NULL
);

CREATE TABLE atendente (
    id                     UUID         PRIMARY KEY,
    nome_completo          VARCHAR(150) NOT NULL,
    cpf                    VARCHAR(11)  NOT NULL UNIQUE,
    email                  VARCHAR(150) NOT NULL UNIQUE,
    unidade_saude_id       UUID         NOT NULL,
    senha                  VARCHAR(100) NOT NULL,
    ativo                  BOOLEAN      NOT NULL DEFAULT TRUE,
    cadastrado_em          TIMESTAMP    NOT NULL
);

CREATE TABLE consulta (
    id                       UUID        PRIMARY KEY,
    gestante_id              UUID        NOT NULL REFERENCES gestante (id),
    unidade_saude_id         UUID        NOT NULL,
    numero_no_pre_natal      INTEGER     NOT NULL,
    agendada_para            TIMESTAMP   NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    atendente_confirmador_id UUID        REFERENCES atendente (id),
    confirmada_em            TIMESTAMP,
    observacao               VARCHAR(500)
);
