-- A empresa ativa e desativa a promoção, como no cupom; promoção desativada não aparece no catálogo de ofertas
ALTER TABLE promocao
    ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
