package com.gerenciador.financas;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a migration V5 num banco que já tem dados, como o banco local de quem roda a aplicação.
 *
 * Os outros testes sempre começam com o banco vazio, então o INSERT da V5 (a "Sem categoria"
 * dos usuários que já existem) não teria o que inserir. Aqui o teste cria um banco separado no
 * mesmo container, aplica as migrations só até a V4, grava dados "antigos" e aí aplica a V5.
 *
 * Não usa o Spring: chama o Flyway direto.
 */
class MigracaoV5Test {

    @Test
    void v5CriaASemCategoriaDosUsuariosQueJaExistemESemArquivarNada() {
        DriverManagerDataSource bancoNovo = criarBancoVazio("migracao_v5");
        JdbcTemplate jdbc = new JdbcTemplate(bancoNovo);

        // 1. Schema como estava antes desta tarefa.
        migrarAte(bancoNovo, "4");

        // 2. Dados que já existiam: dois usuários, e a Ana com uma categoria e uma transação.
        jdbc.update("INSERT INTO usuarios (username, email, password) VALUES ('ana', 'ana@exemplo.com', 'x')");
        jdbc.update("INSERT INTO usuarios (username, email, password) VALUES ('bruno', 'bruno@exemplo.com', 'x')");
        Long ana = jdbc.queryForObject("SELECT id FROM usuarios WHERE username = 'ana'", Long.class);
        jdbc.update("INSERT INTO categoria (nome, usuario_id) VALUES ('Mercado', ?)", ana);
        jdbc.update("""
                INSERT INTO transacao (descricao, valor, data, tipo, categoria_id, usuario_id)
                SELECT 'Compras', 50.00, DATE '2026-11-25', 'DESPESA', id, usuario_id FROM categoria
                """);

        // 3. Aplica a V5.
        migrarAte(bancoNovo, "5");

        // Cada usuário ganhou exatamente uma "Sem categoria".
        List<Map<String, Object>> semCategoria = jdbc.queryForList("""
                SELECT u.username, c.nome FROM categoria c JOIN usuarios u ON u.id = c.usuario_id
                WHERE c.sem_categoria ORDER BY u.username
                """);
        assertThat(semCategoria).extracting(linha -> linha.get("username")).containsExactly("ana", "bruno");
        assertThat(semCategoria).extracting(linha -> linha.get("nome")).containsOnly("Sem categoria");

        // A categoria antiga continua ativa, comum e com a transação dela.
        Map<String, Object> mercado = jdbc.queryForMap(
                "SELECT arquivada, sem_categoria FROM categoria WHERE nome = 'Mercado'");
        assertThat(mercado.get("arquivada")).isEqualTo(false);
        assertThat(mercado.get("sem_categoria")).isEqualTo(false);
        Integer transacoesNoMercado = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transacao t JOIN categoria c ON c.id = t.categoria_id WHERE c.nome = 'Mercado'
                """, Integer.class);
        assertThat(transacoesNoMercado).isEqualTo(1);
    }

    // ---------- auxiliares ----------

    private DriverManagerDataSource criarBancoVazio(String nome) {
        // CREATE DATABASE é executado conectado ao banco principal do container.
        JdbcTemplate principal = new JdbcTemplate(dataSource(IntegracaoTestBase.POSTGRES.getDatabaseName()));
        principal.execute("DROP DATABASE IF EXISTS " + nome);
        principal.execute("CREATE DATABASE " + nome);
        return dataSource(nome);
    }

    private void migrarAte(DriverManagerDataSource dataSource, String versao) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(versao)
                .load()
                .migrate();
    }

    // Conexão com um banco do mesmo container PostgreSQL dos testes de integração (ver IntegracaoTestBase).
    private DriverManagerDataSource dataSource(String banco) {
        String url = "jdbc:postgresql://%s:%d/%s".formatted(
                IntegracaoTestBase.POSTGRES.getHost(), IntegracaoTestBase.POSTGRES.getFirstMappedPort(), banco);
        return new DriverManagerDataSource(url,
                IntegracaoTestBase.POSTGRES.getUsername(), IntegracaoTestBase.POSTGRES.getPassword());
    }
}
