-- Cada categoria e cada transação passa a pertencer a um usuário (usuario_id).

-- ⚠️ Apaga os dados existentes, que não têm dono. Isso só é aceitável porque, hoje, o banco
-- tem apenas dados de teste. Com dados reais, esta migration teria que atribuir um dono a cada
-- linha antes do NOT NULL, em vez de apagar.
-- Transação primeiro, porque ela referencia a categoria (FK).
DELETE FROM transacao;
DELETE FROM categoria;

-- Categoria
ALTER TABLE categoria ADD COLUMN usuario_id BIGINT NOT NULL;
ALTER TABLE categoria ADD CONSTRAINT fk_categoria_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
-- O PostgreSQL não cria índice para FK sozinho, e toda consulta vai filtrar pelo usuário.
CREATE INDEX idx_categoria_usuario ON categoria(usuario_id);

-- Transação
ALTER TABLE transacao ADD COLUMN usuario_id BIGINT NOT NULL;
ALTER TABLE transacao ADD CONSTRAINT fk_transacao_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
CREATE INDEX idx_transacao_usuario ON transacao(usuario_id);
