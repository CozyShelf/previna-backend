CREATE TABLE dados_socioeconomicos (
    gestante_id                   BIGINT         PRIMARY KEY REFERENCES gestante (usuario_id),
    renda_familiar                NUMERIC(12, 2),
    pessoas_domicilio             INTEGER,
    escolaridade                  VARCHAR(30),
    beneficiaria_programa_social  BOOLEAN,
    fator_ponderacao              NUMERIC(4, 2)  NOT NULL
);
