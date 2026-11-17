-- Última barreira: nenhum caminho de débito deixa o saldo negativo
ALTER TABLE gestante ADD CONSTRAINT gestante_saldo_nao_negativo CHECK (saldo_pontos >= 0);
