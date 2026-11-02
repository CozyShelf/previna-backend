-- O médico passa a cadastrar os próprios tipos de exame e consulta, além dos que vieram na V11.
-- criado_por nulo é o tipo padrão: continua disponível para todos os médicos.
ALTER TABLE tipo_atendimento ADD COLUMN criado_por BIGINT REFERENCES medico (usuario_id);
CREATE INDEX ON tipo_atendimento (criado_por);

-- O nome era único na tabela inteira. Agora só precisa ser único dentro do mesmo dono, para dois médicos
-- poderem ter um tipo com o mesmo nome. NULLS NOT DISTINCT trata os nulos como iguais, então os tipos
-- padrão continuam com nome único entre si (Postgres 15+).
ALTER TABLE tipo_atendimento DROP CONSTRAINT tipo_atendimento_nome_key;
ALTER TABLE tipo_atendimento
    ADD CONSTRAINT tipo_atendimento_nome_dono_key UNIQUE NULLS NOT DISTINCT (nome, criado_por);
