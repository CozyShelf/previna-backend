-- Conteúdo educativo publicado pelo admin e lido pela gestante no app. Nasce rascunho
-- (publicado = false) e só aparece para a gestante depois que o admin publica.

CREATE TABLE conteudo_informativo (
    id            BIGSERIAL     PRIMARY KEY,
    autor_id      BIGINT        NOT NULL REFERENCES usuario (id),
    titulo        VARCHAR(150)  NOT NULL,
    categoria     VARCHAR(20)   NOT NULL CHECK (categoria IN ('DICA', 'NOTICIA', 'AVISO')),
    tipo          VARCHAR(20)   NOT NULL CHECK (tipo IN ('TEXTO', 'FOTO', 'VIDEO', 'LINK')),
    resumo        VARCHAR(300),
    corpo         TEXT,
    midia_url     VARCHAR(500),
    publicado     BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em     TIMESTAMP     NOT NULL,
    publicado_em  TIMESTAMP,
    -- Rascunho nunca tem data de publicação; despublicar limpa a data
    CHECK (publicado OR publicado_em IS NULL)
);
-- A gestante só lê os publicados
CREATE INDEX ON conteudo_informativo (publicado);

-- A regra "TEXTO exige corpo, os demais exigem mídia" fica no ConteudoService, e não aqui: a foto
-- enviada só ganha midia_url depois do INSERT (o id entra na URL), então um CHECK barraria o cadastro.

-- Separada do conteúdo, como imagem_produto, para a listagem não carregar os bytes.
-- ON DELETE CASCADE porque o conteúdo tem exclusão de verdade, ao contrário do produto.
CREATE TABLE imagem_conteudo (
    conteudo_id  BIGINT       PRIMARY KEY REFERENCES conteudo_informativo (id) ON DELETE CASCADE,
    conteudo     BYTEA        NOT NULL,
    tipo         VARCHAR(20)  NOT NULL
);
