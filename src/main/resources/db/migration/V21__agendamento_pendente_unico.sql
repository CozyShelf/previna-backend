-- Um pendente só por gestante, tipo e data: duas requisições iguais ao mesmo tempo (duplo clique, duas abas,
-- dois médicos) criavam dois AGENDADO idênticos, e cada confirmação creditava os pontos do mesmo exame.
-- Parcial: depois do desfecho (REALIZADO ou NAO_COMPARECEU) o mesmo exame pode ser marcado de novo na mesma data
CREATE UNIQUE INDEX agendamento_pendente_unico
    ON agendamento (gestante_id, tipo_atendimento_id, data_prevista)
    WHERE status = 'AGENDADO';
