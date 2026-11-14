CREATE TABLE categoria (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

CREATE TABLE transacao (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    valor NUMERIC(15, 2) NOT NULL,
    data DATE NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    categoria_id BIGINT NOT NULL,
    CONSTRAINT fk_transacao_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);
