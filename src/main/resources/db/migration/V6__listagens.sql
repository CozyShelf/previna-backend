-- Permissão dada pelo administrador para o médico ver todas as gestantes, não só as que acompanha
ALTER TABLE medico ADD COLUMN ver_todas_gestantes BOOLEAN NOT NULL DEFAULT FALSE;

-- Busca das listagens sem diferenciar acentos (ex.: "jose" encontra "José")
CREATE EXTENSION IF NOT EXISTS unaccent;
