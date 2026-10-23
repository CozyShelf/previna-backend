INSERT INTO usuario (nome, email, login, senha_hash, perfil, status_conta, criado_em)
VALUES ('Administrador Teste', 'admin@previna.com', 'admin', crypt('admin123', gen_salt('bf', 10)), 'ADMIN', 'ATIVA', NOW())
ON CONFLICT (login) DO NOTHING;
