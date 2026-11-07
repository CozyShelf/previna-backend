-- Senha temporária em claro, para o médico reconsultá-la até o primeiro acesso da gestante, quando é apagada.
-- O login continua conferindo só o senha_hash. Gestantes pré-cadastradas antes desta versão ficam sem ela.
-- IF NOT EXISTS: bancos de dev que rodaram a versão antiga (V13) desta migração já têm a coluna
ALTER TABLE gestante ADD COLUMN IF NOT EXISTS senha_temporaria VARCHAR(20);
