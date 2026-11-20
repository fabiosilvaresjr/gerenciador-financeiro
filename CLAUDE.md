# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Personal finance manager MVP (Portuguese domain language: `Transacao`, `Categoria`, `Usuario`, `receita`/`despesa`). Code identifiers, log/exception messages, and comments are written in Portuguese — keep that convention.

- `backend-financas/` — Spring Boot 4.1 / Java 21 / Maven REST API (the only code that exists so far).
- `docker-compose.yml` — PostgreSQL 16 (`financas` db, port 5432). User/password come from `DB_USER`/`DB_PASSWORD` (no defaults; compose errors out if missing).
- `README.md` — setup guide (`.env`, start the DB, run, test login with curl). Keep it in sync when setup steps change.
- `PLANO.md` — **future target** for the MVP (Angular front-end, `Conta`, `tb_*` schema); it does not describe the current code. `STATUS.md` — current state / progress log; update it when finishing a chunk of work.

## Local secrets (`backend-financas/.env`)

`backend-financas/.env` (git-ignored; template in `.env.example`) holds `JWT_SECRET`, `DB_USER`, `DB_PASSWORD`. Spring imports it via `spring.config.import=optional:file:.env[.properties]` (path relative to the working dir, so run Maven from `backend-financas/`), and Docker Compose reads it via `--env-file`. Real environment variables (shell, IDE Run Configuration) override the `.env`. `application.properties` references `${DB_USER}`, `${DB_PASSWORD}`, `${JWT_SECRET}` with no defaults — never hardcode credentials there.

## Commands

Run the database from the repo root, Maven commands from `backend-financas/`. The project needs JDK 21; if `java` on PATH is older, prefix Maven with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.

```bash
docker compose --env-file backend-financas/.env up -d   # start PostgreSQL (required for the app AND for tests)
./mvnw spring-boot:run               # run the API on :8080
./mvnw clean package                 # build jar (runs tests)
./mvnw verify                        # build + tests (what to run before committing)
./mvnw test                          # all tests
./mvnw test -Dtest=FinancasApplicationTests             # single test class
./mvnw test -Dtest=FinancasApplicationTests#contextLoads # single test method
```

There is no linter/formatter configured. The only test is a `@SpringBootTest` context-load test, which connects to the real PostgreSQL from `application.properties` (no Testcontainers/H2), so it needs the DB up and `DB_USER`/`DB_PASSWORD`/`JWT_SECRET` resolvable.

## Architecture

Layered package structure under `com.gerenciador.financas`:
`controller` (REST, `/api/...`) → `service` (business logic, `@Transactional`) → `repository` (Spring Data JPA) → `model` (JPA entities), with `dto` for API I/O.

- **DTO convention:** separate `*RequestDTO` (Bean Validation annotations, validated with `@Valid` in controllers) and `*ResponseDTO` with a static `fromEntity(...)` mapper. Services return response DTOs, never entities. Not-found cases throw `jakarta.persistence.EntityNotFoundException` (no global `@RestControllerAdvice` exists yet; `AuthController` has a local `@ExceptionHandler` returning 401 for bad credentials, and `AuthService` throws `ResponseStatusException(CONFLICT)` for duplicate username/email).
- **Lombok** is used everywhere (`@Builder`, `@RequiredArgsConstructor` for constructor injection in services/controllers, `@Data` on DTOs). The annotation processor is configured explicitly in `maven-compiler-plugin`.
- **Schema is owned by Flyway**, not Hibernate: `spring.jpa.hibernate.ddl-auto=validate`. Any entity change requires a new migration in `src/main/resources/db/migration/` (`V<n>__descricao.sql`); never edit an applied migration. Actual table names are `categoria`, `transacao`, `usuarios`, `roles`, `usuario_roles` — `PLANO.md` describes a target schema (`tb_*` tables, `Conta` entity, `usuario_id` ownership on transactions) that is **not** implemented yet; transactions/categories currently have no user ownership.

### Security (JWT auth — working)

- `config/SecurityConfig` — stateless, CSRF disabled, `/api/auth/**` and `/error` public, everything else authenticated. Defines the `PasswordEncoder` (BCrypt) and `AuthenticationManager` beans (backed by `UserDetailsServiceImpl` via `DaoAuthenticationProvider`), wires `security/jwt/AuthEntryPointJwt` (JSON 401 body) and registers `config/AuthTokenFilter` before `UsernamePasswordAuthenticationFilter`.
- `config/JwtUtils` — jjwt 0.11.5 (legacy `parserBuilder`/`setSigningKey` API), HS256, subject = username. Reads `financas.app.jwtSecret` (`${JWT_SECRET}`, must be ≥ 256 bits) and `financas.app.jwtExpirationMs` (24 h).
- `config/AuthTokenFilter` — reads `Authorization: Bearer <token>`, validates it, loads the user through `UserDetailsService` and sets a principal of type `UserDetailsImpl` with its roles as authorities.
- `security/services/UserDetailsServiceImpl` + `UserDetailsImpl` load `Usuario` with roles (`ERole`: `ROLE_USER`, `ROLE_ADMIN`, seeded by migration V2).
- `controller/AuthController` + `service/AuthService`: `POST /api/auth/register` (creates user with `ROLE_USER`, returns `UsuarioResponseDTO`, 201) and `POST /api/auth/login` (returns `JwtResponseDTO` with token, 200). DTOs live in `dto/auth/`.
- `JwtUtils` and `AuthTokenFilter` live in `config/` (they were moved from `security/jwt/`); don't recreate duplicates there. Only `AuthEntryPointJwt` stays in `security/jwt/`.
- Leftover: `spring.security.user.name/password` in `application.properties` is unused with JWT and slated for removal.
