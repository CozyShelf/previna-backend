-- Cópia do cupom no momento do resgate: a empresa pode editar o cupom (desconto, validade, pontos e até o produto)
-- e renomear o produto depois, mas o que a gestante resgatou não muda, nem no cupom resgatado nem no extrato.
-- cupom_id continua para saber de onde o resgate veio.
ALTER TABLE cupom_resgatado
    ADD COLUMN produto_id     BIGINT         REFERENCES produto (id),
    ADD COLUMN produto_nome   VARCHAR(150),
    ADD COLUMN empresa_id     BIGINT         REFERENCES empresa_parceira (usuario_id),
    ADD COLUMN empresa_nome   VARCHAR(150),
    ADD COLUMN desconto       NUMERIC(5, 2)  CHECK (desconto > 0 AND desconto <= 100),
    ADD COLUMN data_validade  DATE,
    ADD COLUMN pontos_gastos  INTEGER        CHECK (pontos_gastos > 0);

-- Resgates já feitos herdam o cupom como está hoje (é o melhor que se tem); os pontos vêm do débito, que é o que a
-- gestante de fato gastou, e do cupom só se o débito não existir
UPDATE cupom_resgatado cr SET
    produto_id    = c.produto_id,
    produto_nome  = p.nome,
    empresa_id    = c.empresa_id,
    empresa_nome  = e.razao_social,
    desconto      = c.desconto,
    data_validade = c.data_validade,
    pontos_gastos = coalesce((SELECT t.pontos FROM transacao_pontos t WHERE t.cupom_resgatado_id = cr.id),
                             c.pontos_necessarios)
FROM cupom c
    JOIN produto p ON p.id = c.produto_id
    JOIN empresa_parceira e ON e.usuario_id = c.empresa_id
WHERE c.id = cr.cupom_id;

ALTER TABLE cupom_resgatado
    ALTER COLUMN produto_id    SET NOT NULL,
    ALTER COLUMN produto_nome  SET NOT NULL,
    ALTER COLUMN empresa_id    SET NOT NULL,
    ALTER COLUMN empresa_nome  SET NOT NULL,
    ALTER COLUMN desconto      SET NOT NULL,
    ALTER COLUMN data_validade SET NOT NULL,
    ALTER COLUMN pontos_gastos SET NOT NULL;

