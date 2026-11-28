-- Refresh tokens: servem só para pedir um access token (JWT) novo quando o atual vence.
-- O token em si nunca é gravado: guardamos o SHA-256 dele (token_hash).
CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,   -- SHA-256 do token, em hexadecimal (64 caracteres)
    familia     UUID        NOT NULL,   -- todos os tokens que nasceram do mesmo login (uma sessão)
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,            -- preenchido quando o token é trocado por um novo (rotação)
    revogado_em TIMESTAMPTZ,            -- preenchido no logout ou quando um reuso é detectado
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)  -- também serve de índice para a busca pelo hash
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token(usuario_id);
CREATE INDEX idx_refresh_token_familia ON refresh_token(familia);
