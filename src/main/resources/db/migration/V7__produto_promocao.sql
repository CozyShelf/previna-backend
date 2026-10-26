CREATE TABLE produto (
    id          BIGSERIAL      PRIMARY KEY,
    empresa_id  BIGINT         NOT NULL REFERENCES empresa_parceira (usuario_id),
    nome        VARCHAR(150)   NOT NULL,
    descricao   TEXT,
    categoria   VARCHAR(50),
    preco       NUMERIC(10, 2),
    imagem_url  VARCHAR(500)
);
CREATE INDEX ON produto (empresa_id);

-- Separada do produto, como o logo da empresa, para a listagem não carregar as imagens
CREATE TABLE imagem_produto (
    produto_id  BIGINT       PRIMARY KEY REFERENCES produto (id),
    conteudo    BYTEA        NOT NULL,
    tipo        VARCHAR(20)  NOT NULL
);

-- desconto é percentual sobre o preço dos produtos da promoção
CREATE TABLE promocao (
    id           BIGSERIAL      PRIMARY KEY,
    empresa_id   BIGINT         NOT NULL REFERENCES empresa_parceira (usuario_id),
    titulo       VARCHAR(150)   NOT NULL,
    descricao    TEXT,
    desconto     NUMERIC(5, 2)  NOT NULL CHECK (desconto > 0 AND desconto <= 100),
    data_inicio  DATE           NOT NULL,
    data_fim     DATE           NOT NULL CHECK (data_fim >= data_inicio)
);
CREATE INDEX ON promocao (empresa_id);

CREATE TABLE promocao_produto (
    promocao_id  BIGINT  REFERENCES promocao (id),
    produto_id   BIGINT  REFERENCES produto (id),
    PRIMARY KEY (promocao_id, produto_id)
);
