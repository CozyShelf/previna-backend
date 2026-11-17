-- D12: uma promoção ATIVA só por empresa e categoria em cada período (os dois dias inclusos). A exclusão barra no
-- banco os cadastros, edições e reativações simultâneos; desativada não entra na conta, então desativar nunca conflita
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE promocao ADD CONSTRAINT promocao_sem_sobreposicao
    EXCLUDE USING gist (empresa_id WITH =, categoria WITH =,
                        daterange(data_inicio, data_fim, '[]') WITH &&)
    WHERE (ativo);
