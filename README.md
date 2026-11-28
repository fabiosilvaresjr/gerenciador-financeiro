# Gerenciador de Finanças

API REST para controle de finanças pessoais (categorias, transações de receita/despesa e usuários com login JWT).
Hoje existe só o back-end; o front-end Angular e o restante do MVP estão planejados.

## Stack

- Java 21, Spring Boot 4.1 (Web, Data JPA, Validation, Security), Maven (wrapper `./mvnw`)
- JWT com jjwt 0.11.5 (HS256), senhas com BCrypt
- PostgreSQL 16 via Docker Compose, schema versionado com Flyway (`ddl-auto=validate`)
- Lombok
- Testes de integração com JUnit 5, MockMvc e Testcontainers (PostgreSQL 16)

## Estrutura

```
.github/workflows/ci.yml   # CI: ./mvnw verify em cada PR e push na main
docker-compose.yml         # PostgreSQL 16 (banco "financas")
backend-financas/          # API Spring Boot
  .env.example             # modelo do .env (segredos locais, fora do Git)
  src/main/resources/db/migration/   # migrations Flyway (V1 a V4)
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

> A migration V3 dá um dono (`usuario_id`) a categorias e transações e, para isso, **apaga as categorias e
> transações que já existiam** no banco (eram só dados de teste). Os usuários continuam.

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

No cadastro, o usuário já ganha as próprias categorias padrão: Alimentação, Moradia, Transporte, Saúde, Educação,
Lazer, Salário e Outros. Elas são criadas na mesma transação do usuário: se uma parte falhar, nada é gravado.

Fazer login e pegar o token (público):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teste","password":"senha123"}'
# 200 → {"token":"eyJ...","tipo":"Bearer","refreshToken":"q3W...","id":1,"username":"teste",...}
```

Chamar um endpoint protegido com o token:

```bash
TOKEN=eyJ...   # o valor de "token" da resposta acima
curl http://localhost:8080/api/categorias -H "Authorization: Bearer $TOKEN"
# 200 → [{"id":1,"nome":"Alimentação"}, ...]   (as 8 categorias padrão; sem o header: 401)
```

Senha errada no login também retorna `401`.

### Renovar o token e sair

O `token` (access token) vale **15 min** (`financas.app.jwtExpirationMs`). Para pegar um novo sem logar de novo,
troque o `refreshToken`, que vale **7 dias** (`financas.app.refreshExpirationMs`):

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"q3W..."}'
# 200 → mesmo formato do login, com um token novo e um refreshToken novo
```

- Cada refresh token vale **uma vez**: o refresh devolve um novo, e o antigo deixa de funcionar (rotação).
- Se um refresh token já usado for enviado de novo, a API entende que ele pode ter sido copiado e **encerra a sessão**:
  todos os refresh tokens daquele login param de funcionar. As sessões de outros logins (outro aparelho) continuam.
- Token desconhecido, vencido, já usado ou encerrado: sempre `401` com a mesma mensagem.
- O banco guarda só o hash SHA-256 do refresh token, nunca o valor.

Para sair, envie o refresh token ao logout (`204`, mesmo se o token não existir):

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"q3W..."}'
```

O logout encerra a sessão do refresh token. O access token já emitido continua valendo até vencer (no máximo 15 min),
porque a API não consulta o banco para validar o JWT.

## Endpoints existentes

| Método | Caminho | Acesso |
|---|---|---|
| `POST` | `/api/auth/register` | público |
| `POST` | `/api/auth/login` | público |
| `POST` | `/api/auth/refresh` | público (recebe o refresh token) |
| `POST` | `/api/auth/logout` | público (recebe o refresh token) |
| `GET`, `POST` | `/api/categorias` | token |
| `GET`, `PUT`, `DELETE` | `/api/categorias/{id}` | token |
| `GET`, `POST` | `/api/transacoes` | token |
| `GET`, `PUT`, `DELETE` | `/api/transacoes/{id}` | token |

Categorias e transações pertencem ao usuário do token: cada um só lista, lê, altera e apaga os próprios dados.
O ID de outro usuário responde `404`, igual a um ID que não existe. A categoria de uma transação também tem
que ser do mesmo usuário (senão, `404`).

## Testes

Os testes de integração usam **Testcontainers**: cada execução sobe um PostgreSQL 16 descartável no Docker,
roda as migrations do Flyway nele e o remove no final. Basta o **Docker rodando**. Não é preciso subir o banco
do `docker compose` nem preencher o `.env`, porque os testes usam um segredo JWT próprio.

Em `backend-financas/`:

```bash
./mvnw verify                                  # build + todos os testes
./mvnw test -Dtest=CategoriaIntegracaoTest     # uma classe
./mvnw test -Dtest=AuthIntegracaoTest#loginComSenhaErradaResponde401   # um teste
```

Na primeira execução o Docker baixa a imagem `postgres:16`, então demora um pouco mais.

O que é coberto (`src/test/java/.../controller/`):

- `AuthIntegracaoTest`: cadastro (201, 409 username/email repetido, 400 com a lista `campos`), login (200 com token,
  401 senha errada ou usuário inexistente) e acesso a rota protegida sem token, com token inválido e com token válido.
- `RefreshTokenIntegracaoTest`: login com refresh token, só o hash no banco, validade de 15 min e de 7 dias, rotação,
  reuso que encerra a sessão (sem afetar outra), refresh vencido ou desconhecido, logout (e sua idempotência), o refresh
  da Ana gerando um token da Ana, e dois refreshes simultâneos com o mesmo token (só um funciona).
- `FiltroJwtIntegracaoTest`: token expirado, token assinado com outra chave, token válido de um usuário que não existe
  mais e header `Authorization` sem o prefixo `Bearer` (token puro ou `Basic`): todos respondem 401 com o corpo JSON padrão.
- `CategoriaIntegracaoTest` e `TransacaoIntegracaoTest`: CRUD completo, 404 para ID inexistente, 400 de validação,
  409 ao excluir categoria com transações (e a transação continua existindo) e 404 ao criar transação com categoria inexistente.
- `IsolamentoIntegracaoTest`: o usuário B não vê os dados do usuário A na listagem e recebe 404 ao ler, alterar ou
  apagar a categoria ou a transação de A, ou ao usar a categoria de A numa transação. Depois de cada tentativa, o teste
  confere com o token de A que os dados dele continuam iguais.
- `CategoriasPadraoIntegracaoTest`: o cadastro cria as 8 categorias padrão, uma cópia para cada usuário, que podem ser
  usadas em transações; um cadastro recusado (409) não cria categorias; e, se a criação das categorias falhar
  (simulada com `@MockitoSpyBean`), o usuário também não é gravado (rollback).

A configuração comum (container, propriedades, limpeza das tabelas antes de cada teste e atalhos como
`registrarELogar`) fica em `IntegracaoTestBase`.

## Cobertura de testes (JaCoCo)

O plugin do JaCoCo está ligado ao `verify`: o `prepare-agent` acompanha quais linhas os testes executam e o
`report` gera o relatório no final. Em `backend-financas/`:

```bash
./mvnw verify
```

Abra `backend-financas/target/site/jacoco/index.html` no navegador. Dá para navegar por pacote e classe até o
código-fonte, com as linhas cobertas em verde, as não cobertas em vermelho e os `if` cobertos pela metade em amarelo.
Na mesma pasta ficam `jacoco.xml` e `jacoco.csv`, para ferramentas. O código gerado pelo Lombok fica fora da conta
(`lombok.config`).

Números atuais (2026-11-27, 47 testes):

| Métrica | Cobertura |
|---|---|
| Linhas | 96% (223/232) |
| Branches | 64% (14/22) |
| Métodos | 95% (76/80) |
| Instruções | 96% (998/1042) |

No GitHub Actions o mesmo relatório é publicado como artefato `relatorio-jacoco` em cada execução do workflow
(aba **Actions** → execução → **Artifacts**).
