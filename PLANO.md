# Plano do MVP - Gestor de Finanças Pessoais

## 1. Visão Geral e Arquitetura

O sistema é uma aplicação web para gerenciamento financeiro pessoal básico (controle de receitas, despesas, contas e categorias), composto por:

- **Back-end:** Java (Spring Boot)
  - Spring Web (API RESTful)
  - Spring Data JPA (Persistência)
  - Spring Security + JWT (Autenticação e autorização)
  - Bean Validation
- **Banco de Dados:** PostgreSQL
- **Front-end:** Angular
  - Angular Material ou Bootstrap/Tailwind CSS
  - Consumo via HttpClient e TypeScript
  - Gerenciamento de estado reativo via Signals / RxJS
- **Comunicação:** REST API (JSON)

---

## 2. Entidades Principais do Banco de Dados

### 2.1. Usuário (`tb_usuario`)
- `id`: BIGSERIAL / UUID (PK)
- `nome`: VARCHAR(100) NOT NULL
- `email`: VARCHAR(100) UNIQUE NOT NULL
- `senha`: VARCHAR(255) NOT NULL (hash BCrypt)
- `criado_em`: TIMESTAMP NOT NULL DEFAULT NOW()

### 2.2. Conta (`tb_conta`)
- `id`: BIGSERIAL / UUID (PK)
- `usuario_id`: BIGINT / UUID (FK -> tb_usuario)
- `nome`: VARCHAR(50) NOT NULL (ex: "Nubank", "Carteira", "Itaú")
- `tipo`: VARCHAR(20) NOT NULL (CORRENTE, CARTEIRA, POUPANCA, INVESTIMENTO)
- `saldo_inicial`: NUMERIC(15, 2) NOT NULL DEFAULT 0.00
- `saldo_atual`: NUMERIC(15, 2) NOT NULL DEFAULT 0.00
- `criado_em`: TIMESTAMP NOT NULL DEFAULT NOW()

### 2.3. Categoria (`tb_categoria`)
- `id`: BIGSERIAL / UUID (PK)
- `usuario_id`: BIGINT / UUID (FK -> tb_usuario, nulo se padrão global)
- `nome`: VARCHAR(50) NOT NULL (ex: "Alimentação", "Salário", "Lazer")
- `tipo`: VARCHAR(20) NOT NULL (RECEITA, DESPESA)
- `cor`: VARCHAR(7) (ex: "#FF5733")
- `icone`: VARCHAR(50)

### 2.4. Transação / Lançamento (`tb_transacao`)
- `id`: BIGSERIAL / UUID (PK)
- `usuario_id`: BIGINT / UUID (FK -> tb_usuario)
- `conta_id`: BIGINT / UUID (FK -> tb_conta)
- `categoria_id`: BIGINT / UUID (FK -> tb_categoria)
- `descricao`: VARCHAR(100) NOT NULL
- `valor`: NUMERIC(15, 2) NOT NULL
- `tipo`: VARCHAR(20) NOT NULL (RECEITA, DESPESA, TRANSFERENCIA)
- `data_transacao`: DATE NOT NULL
- `pago`: BOOLEAN NOT NULL DEFAULT TRUE
- `criado_em`: TIMESTAMP NOT NULL DEFAULT NOW()

---

## 3. Roadmap Enxuto (MVP)

### Etapa 1: Setup do Ambiente e Infraestrutura
- Inicialização do projeto Spring Boot (`pom.xml` / `build.gradle`).
- Configuração do `docker-compose.yml` (PostgreSQL).
- Criação e scaffold do app Angular.

### Etapa 2: Autenticação e Usuários
- Criação do cadastro e login com Spring Security e geração de token JWT.
- Telas de Login e Cadastro no Angular com Guards de rota.

### Etapa 3: Gestão de Contas e Categorias
- CRUD de Contas e listagem de saldos.
- CRUD e carga inicial de Categorias padrão.
- Telas de listagem e formulários no Angular.

### Etapa 4: Gestão de Transações
- Lançamento de despesas e receitas.
- Atualização dinâmica do saldo das contas.
- Filtro de transações por data/mês e categoria.

### Etapa 5: Dashboard e Métricas Básicas
- Painel principal com saldo total consolidado.
- Resumo mensal de receitas vs. despesas.
- Gráfico simples de despesas por categoria.
