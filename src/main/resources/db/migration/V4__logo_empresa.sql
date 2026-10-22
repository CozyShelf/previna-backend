-- Separado da empresa_parceira para a imagem não ser carregada a cada login
CREATE TABLE logo_empresa (
    empresa_id  BIGINT       PRIMARY KEY REFERENCES empresa_parceira (usuario_id),
    conteudo    BYTEA        NOT NULL,
    tipo        VARCHAR(20)  NOT NULL
);
