-- Excluir um tipo de atendimento passa a ser exclusão lógica: a linha fica, com ativo = FALSE, porque o
-- agendamento tem chave estrangeira para cá e o histórico da gestante perderia o nome do que ela fez.
-- O médico não escolhe mais o status no formulário: ativo = FALSE significa excluído.

-- O nome só precisa ser único entre os tipos vivos. Com a UNIQUE antiga, um nome excluído ficaria
-- reservado para sempre e o médico nunca mais poderia cadastrar outro tipo com ele.
ALTER TABLE tipo_atendimento DROP CONSTRAINT tipo_atendimento_nome_dono_key;
CREATE UNIQUE INDEX tipo_atendimento_nome_dono_key
    ON tipo_atendimento (nome, criado_por) NULLS NOT DISTINCT
    WHERE ativo;
