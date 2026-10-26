-- Vale para uma categoria geral ou para um produto da empresa, nunca os dois; desconto é percentual
CREATE TABLE cupom (
    id                     BIGSERIAL      PRIMARY KEY,
    empresa_id             BIGINT         NOT NULL REFERENCES empresa_parceira (usuario_id),
    categoria              VARCHAR(20)    CHECK (categoria IN ('FRALDAS', 'ROUPAS', 'REMEDIOS')),
    produto_id             BIGINT         REFERENCES produto (id),
    descricao              VARCHAR(150)   NOT NULL,
    desconto               NUMERIC(5, 2)  NOT NULL CHECK (desconto > 0 AND desconto <= 100),
    data_validade          DATE           NOT NULL,
    quantidade_disponivel  INTEGER        NOT NULL CHECK (quantidade_disponivel >= 0),
    pontos_necessarios     INTEGER        NOT NULL CHECK (pontos_necessarios > 0),
    CHECK ((categoria IS NULL) <> (produto_id IS NULL))
);
CREATE INDEX ON cupom (empresa_id);
