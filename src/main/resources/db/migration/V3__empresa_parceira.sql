-- Empresa parceira não tem CPF
ALTER TABLE usuario ALTER COLUMN cpf DROP NOT NULL;

CREATE TABLE empresa_parceira (
    usuario_id    BIGINT        PRIMARY KEY REFERENCES usuario (id),
    razao_social  VARCHAR(150)  NOT NULL,
    cnpj          VARCHAR(14)   NOT NULL UNIQUE,
    telefone      VARCHAR(11),
    responsavel   VARCHAR(150),
    endereco      VARCHAR(255),
    logo_url      VARCHAR(500),
    descricao     TEXT,
    segmento      VARCHAR(50)
);
