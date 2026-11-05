-- Senha temporária em claro, para o médico reconsultá-la até o primeiro acesso da gestante, quando é apagada.
-- O login continua conferindo só o senha_hash. Gestantes pré-cadastradas antes desta versão ficam sem ela.
ALTER TABLE gestante ADD COLUMN senha_temporaria VARCHAR(20);
