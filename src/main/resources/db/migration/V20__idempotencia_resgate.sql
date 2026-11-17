-- Chave enviada pelo app a cada confirmação de resgate: o toque repetido devolve o resgate já feito.
-- Nula nos resgates antigos; nulos não colidem no UNIQUE, então não há o que limpar
ALTER TABLE cupom_resgatado ADD COLUMN chave_idempotencia UUID;
CREATE UNIQUE INDEX cupom_resgatado_idempotencia_key ON cupom_resgatado (gestante_id, chave_idempotencia);
