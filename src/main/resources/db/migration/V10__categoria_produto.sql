-- A categoria geral liga o produto às promoções: a promoção vale para uma categoria e o cupom só para um produto

-- Texto livre que não é fralda, roupa nem remédio fica sem categoria
UPDATE produto SET categoria = CASE unaccent(lower(trim(categoria)))
    WHEN 'fralda' THEN 'FRALDAS' WHEN 'fraldas' THEN 'FRALDAS'
    WHEN 'roupa' THEN 'ROUPAS' WHEN 'roupas' THEN 'ROUPAS'
    WHEN 'remedio' THEN 'REMEDIOS' WHEN 'remedios' THEN 'REMEDIOS'
END;
ALTER TABLE produto
    ALTER COLUMN categoria TYPE VARCHAR(20),
    ADD CHECK (categoria IN ('FRALDAS', 'ROUPAS', 'REMEDIOS'));

-- Promoção já cadastrada herda a categoria de um dos seus produtos; se nenhum tiver categoria, a migração para
-- (sem apagar nada) e a categoria dessa promoção precisa ser definida à mão
ALTER TABLE promocao ADD COLUMN categoria VARCHAR(20) CHECK (categoria IN ('FRALDAS', 'ROUPAS', 'REMEDIOS'));
UPDATE promocao p SET categoria = (
    SELECT min(pr.categoria) FROM promocao_produto pp JOIN produto pr ON pr.id = pp.produto_id
    WHERE pp.promocao_id = p.id);
ALTER TABLE promocao ALTER COLUMN categoria SET NOT NULL;
DROP TABLE promocao_produto;

-- Cupom de categoria já cadastrado faz a migração parar (sem apagar nada); a categoria sai junto com a sua restrição
ALTER TABLE cupom
    ALTER COLUMN produto_id SET NOT NULL,
    DROP COLUMN categoria;
