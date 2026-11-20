# Status do Projeto

> Estado real do código. O `PLANO.md` descreve a **meta futura** do MVP (front-end Angular, entidade `Conta`,
> schema `tb_*`) e ainda não corresponde ao que está implementado.

## Status Geral

- **Fase atual:** back-end com CRUD básico e autenticação JWT funcionando (Etapa 2 do `PLANO.md`, só a parte do back-end).
- **Front-end:** ainda não criado.

---

## O que já existe

- **Infra:** `docker-compose.yml` com PostgreSQL 16; credenciais lidas de `DB_USER`/`DB_PASSWORD`
  (`docker compose --env-file backend-financas/.env up -d`). Segredos locais em `backend-financas/.env` (fora do Git),
  modelo em `.env.example`. Passo a passo no `README.md`.
- **Back-end:** Spring Boot 4.1 / Java 21 / Maven, schema controlado pelo Flyway (`ddl-auto=validate`).
  - Migrations: `V1` (`categoria`, `transacao`) e `V2` (`usuarios`, `roles`, `usuario_roles` + seed `ROLE_USER`/`ROLE_ADMIN`).
  - Entidades: `Categoria`, `Transacao` (+ `TipoTransacao`), `Usuario`, `Role` (+ `ERole`).
  - CRUD completo de Categorias (`/api/categorias`) e Transações (`/api/transacoes`).
  - Autenticação JWT: `POST /api/auth/register` e `POST /api/auth/login` (públicos); demais rotas exigem
    `Authorization: Bearer <token>`. Senhas com BCrypt; 401 em JSON para token ausente/inválido e credenciais erradas.
- **Testes:** só o `contextLoads` (`@SpringBootTest` contra o PostgreSQL local).

---

## Limitações conhecidas

- Categorias e transações não têm dono (`usuario_id`): todo usuário logado vê os mesmos dados.
- Não há handler global de exceções (`EntityNotFoundException` vira 500).
- Entidade `Conta` e saldo ainda não existem.
- `spring.security.user.name/password` sobrando no `application.properties`.

---

## Próximos passos

- [ ] Handler global de exceções (`@RestControllerAdvice`).
- [ ] `usuario_id` em `categoria`/`transacao` (nova migration) e filtro pelo usuário logado.
- [ ] Entidade `Conta` com saldo inicial/atual, atualizado pelas transações.
- [ ] Filtros de transação por mês/data e categoria.
- [ ] Testes de integração de auth e CRUD.
- [ ] Front-end Angular (login/cadastro, guard, interceptor; telas de Categorias, Contas e Transações; dashboard).
