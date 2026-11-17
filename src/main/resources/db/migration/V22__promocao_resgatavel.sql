-- Promoção resgatável: sem pontos, uma vez por gestante, com código próprio. A tabela de resgates passa a guardar
-- cupons e promoções (tipo), cada um com a sua cópia; o CHECK amarra quais colunas cada tipo preenche.
-- O índice único só vale para promoções: nos cupons promocao_id é nulo, e nulos não colidem (cupom continua N vezes).
ALTER TABLE cupom_resgatado
    ADD COLUMN tipo        VARCHAR(10) NOT NULL DEFAULT 'CUPOM' CHECK (tipo IN ('CUPOM', 'PROMOCAO')),
    ADD COLUMN promocao_id BIGINT      REFERENCES promocao (id),
    ADD COLUMN titulo      VARCHAR(150),
    ADD COLUMN categoria   VARCHAR(20) CHECK (categoria IN ('FRALDAS', 'ROUPAS', 'REMEDIOS')),
    ALTER COLUMN cupom_id      DROP NOT NULL,
    ALTER COLUMN produto_id    DROP NOT NULL,
    ALTER COLUMN produto_nome  DROP NOT NULL,
    ALTER COLUMN pontos_gastos DROP NOT NULL;
ALTER TABLE cupom_resgatado ALTER COLUMN tipo DROP DEFAULT;

ALTER TABLE cupom_resgatado ADD CONSTRAINT cupom_resgatado_tipo_coerente CHECK (
    (tipo = 'CUPOM'    AND cupom_id IS NOT NULL AND promocao_id IS NULL
                       AND produto_id IS NOT NULL AND produto_nome IS NOT NULL AND pontos_gastos IS NOT NULL)
 OR (tipo = 'PROMOCAO' AND promocao_id IS NOT NULL AND cupom_id IS NULL
                       AND produto_id IS NULL AND pontos_gastos IS NULL AND titulo IS NOT NULL AND categoria IS NOT NULL));

CREATE UNIQUE INDEX cupom_resgatado_promocao_uma_vez ON cupom_resgatado (gestante_id, promocao_id);
CREATE INDEX ON cupom_resgatado (promocao_id);
