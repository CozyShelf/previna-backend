INSERT INTO usuario (nome, cpf, email, login, senha_hash, perfil, status_conta, criado_em)
VALUES ('Médico Teste', '00000000000', 'medico@previna.com', 'medico', crypt('medico123', gen_salt('bf', 10)), 'MEDICO', 'ATIVA', NOW())
ON CONFLICT (login) DO NOTHING;

INSERT INTO medico (usuario_id, crm)
SELECT id, 'CRM-SP 000000' FROM usuario WHERE login = 'medico'
ON CONFLICT DO NOTHING;
