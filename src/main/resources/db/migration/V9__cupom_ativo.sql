-- A empresa ativa e desativa o cupom em vez de limitar a quantidade; a descrição é montada a partir do desconto e da categoria ou do produto
ALTER TABLE cupom
    DROP COLUMN quantidade_disponivel,
    DROP COLUMN descricao,
    ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
