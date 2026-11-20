# Gerenciador de Finanças

API REST para controle de finanças pessoais (categorias, transações de receita/despesa e usuários com login JWT).
Hoje existe só o back-end; o front-end Angular e o restante do MVP estão descritos em [`PLANO.md`](PLANO.md) como meta futura.

## Stack

- Java 21, Spring Boot 4.1 (Web, Data JPA, Validation, Security), Maven (wrapper `./mvnw`)
- JWT com jjwt 0.11.5 (HS256), senhas com BCrypt
- PostgreSQL 16 via Docker Compose, schema versionado com Flyway (`ddl-auto=validate`)
- Lombok

## Estrutura

```
docker-compose.yml     # PostgreSQL 16 (banco "financas")
backend-financas/      # API Spring Boot
  .env.example         # modelo do .env (segredos locais, fora do Git)
  src/main/resources/db/migration/   # migrations Flyway (V1, V2)
```

## 1. Configurar o `.env`

O `.env` guarda os segredos locais e é lido tanto pelo backend quanto pelo Docker Compose. Ele **não** vai para o Git.

```bash
cd backend-financas
cp .env.example .env
```

Preencha:

```properties
JWT_SECRET=<saída de: openssl rand -base64 64 | tr -d '\n'>
DB_USER=admin
DB_PASSWORD=<senha do banco>
```

- `JWT_SECRET` precisa ter pelo menos 256 bits (o comando acima gera 512) e ficar em uma linha só.
- `DB_USER`/`DB_PASSWORD` definem o usuário do PostgreSQL **na primeira vez** que o volume do banco é criado. Depois disso,
  trocar o valor no `.env` não muda a senha no banco (veja "Trocar a senha do banco" abaixo).
- Variáveis de ambiente do shell ou da Run Configuration da IDE têm prioridade sobre o `.env`.

## 2. Subir o banco

Na **raiz** do repositório:

```bash
docker compose --env-file backend-financas/.env up -d
```

O `--env-file` é necessário porque o `.env` fica em `backend-financas/`, e não na raiz. Sem ele, o Compose
para com o erro `required variable DB_USER is missing a value`.

Verificar se está no ar:

```bash
docker exec financas-db pg_isready -U admin -d financas
```

Parar (mantendo os dados): `docker compose --env-file backend-financas/.env stop`.

### Trocar a senha do banco

Com o banco no ar, altere a senha no PostgreSQL e depois atualize `DB_PASSWORD` no `.env`:

```bash
docker exec -it financas-db psql -U admin -d financas -c "ALTER USER admin WITH PASSWORD 'nova-senha';"
```

## 3. Rodar a API

Em `backend-financas/` (o banco precisa estar no ar):

```bash
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. O Flyway aplica as migrations na primeira execução
(tabelas `categoria`, `transacao`, `usuarios`, `roles`, `usuario_roles` e os papéis `ROLE_USER`/`ROLE_ADMIN`).

> Se aparecer `class file version 65.0 ... up to 61.0`, o `java` do terminal não é o 21. Rode com
> `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw spring-boot:run` (ajuste o caminho do seu JDK 21).

## 4. Testar o login

Cadastrar um usuário (público):

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"teste","email":"teste@exemplo.com","password":"senha123"}'
# 201 → {"id":1,"username":"teste","email":"teste@exemplo.com","roles":["ROLE_USER"]}
```

Fazer login e pegar o token (público):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teste","password":"senha123"}'
# 200 → {"token":"eyJ...","tipo":"Bearer","id":1,"username":"teste",...}
```

Chamar um endpoint protegido com o token:

```bash
TOKEN=eyJ...   # o valor de "token" da resposta acima
curl http://localhost:8080/api/categorias -H "Authorization: Bearer $TOKEN"
# 200 → []   (sem o header: 401)
```

Senha errada no login também retorna `401`. O token expira em 24 h (`financas.app.jwtExpirationMs`).

## Endpoints existentes

| Método | Caminho | Acesso |
|---|---|---|
| `POST` | `/api/auth/register` | público |
| `POST` | `/api/auth/login` | público |
| `GET`, `POST` | `/api/categorias` | token |
| `GET`, `PUT`, `DELETE` | `/api/categorias/{id}` | token |
| `GET`, `POST` | `/api/transacoes` | token |
| `GET`, `PUT`, `DELETE` | `/api/transacoes/{id}` | token |

Categorias e transações ainda não pertencem a um usuário: todo usuário logado vê os mesmos dados.

## Testes

Em `backend-financas/`, com o banco no ar e o `.env` preenchido:

```bash
./mvnw verify
```

Hoje há só um teste que sobe o contexto do Spring contra o PostgreSQL local (não usa H2 nem Testcontainers).
