-- Separada do logo da empresa: a foto da gestante é dado pessoal e só ela acessa
CREATE TABLE foto_gestante (
    gestante_id  BIGINT       PRIMARY KEY REFERENCES gestante (usuario_id),
    conteudo     BYTEA        NOT NULL,
    tipo         VARCHAR(20)  NOT NULL
);
