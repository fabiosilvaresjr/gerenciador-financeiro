-- Arquivar categoria + categoria "Sem categoria" de cada usuário.

-- Categoria arquivada: some da listagem padrão e não entra em transações novas,
-- mas as transações antigas continuam ligadas a ela.
ALTER TABLE categoria ADD COLUMN arquivada BOOLEAN NOT NULL DEFAULT false;

-- Marca a categoria "Sem categoria" do usuário: é para ela que vai uma transação criada sem
-- categoria. Ela não pode ser arquivada, renomeada nem excluída (regra no CategoriaService).
ALTER TABLE categoria ADD COLUMN sem_categoria BOOLEAN NOT NULL DEFAULT false;

-- No máximo uma "Sem categoria" por usuário. É um índice único "parcial": só vale para as linhas
-- com sem_categoria = true, então as outras categorias do usuário não entram na conta.
CREATE UNIQUE INDEX uk_categoria_sem_categoria_por_usuario ON categoria(usuario_id) WHERE sem_categoria;

-- O banco também garante que a "Sem categoria" nunca fica arquivada.
ALTER TABLE categoria ADD CONSTRAINT ck_categoria_sem_categoria_nao_arquivada
    CHECK (NOT (sem_categoria AND arquivada));

-- Usuários que já existem também ganham a sua "Sem categoria" (os novos ganham no cadastro).
INSERT INTO categoria (nome, usuario_id, sem_categoria)
SELECT 'Sem categoria', id, true FROM usuarios;
