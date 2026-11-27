package com.gerenciador.financas;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração.
 *
 * - Sobe um PostgreSQL 16 descartável no Docker (Testcontainers). Não usa o banco local nem o .env.
 * - O Flyway roda as migrations de verdade nesse banco, então o schema testado é o mesmo da aplicação.
 * - MockMvc faz as requisições HTTP passando pelo filtro de segurança (JWT) e pelos controllers.
 * - Antes de cada teste, as tabelas são esvaziadas, para um teste não depender do outro.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegracaoTestBase {

    // Um único container para todas as classes de teste (padrão "singleton"): ele é iniciado
    // uma vez e o Testcontainers o remove quando a JVM dos testes termina.
    // Não usamos @Container/@Testcontainers porque eles parariam o container ao fim de cada classe,
    // e o contexto do Spring (reaproveitado entre as classes) ficaria apontando para um banco morto.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    // Sobrescreve o application.properties: aponta para o container e define um segredo JWT só de teste
    // (assim os testes não precisam de DB_USER, DB_PASSWORD nem JWT_SECRET).
    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("financas.app.jwtSecret", () -> "segredo-de-teste-com-pelo-menos-256-bits-0123456789");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    // protected: alguns testes conferem o banco direto, sem passar pela API.
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBanco() {
        // "roles" fica: é preenchida pela migration V2 e não muda nos testes.
        jdbcTemplate.execute("TRUNCATE TABLE transacao, categoria, usuario_roles, usuarios RESTART IDENTITY");
    }

    // ---------- Atalhos usados pelos testes ----------

    protected void registrar(String username, String email, String senha) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "%s", "password": "%s"}
                                """.formatted(username, email, senha)))
                .andExpect(status().isCreated());
    }

    protected String login(String username, String senha) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, senha)))
                .andExpect(status().isOk())
                .andReturn();
        return lerCampo(resultado, "$.token");
    }

    /** Cadastra um usuário novo e devolve o valor pronto para o header Authorization. */
    protected String registrarELogar(String username) throws Exception {
        registrar(username, username + "@exemplo.com", "senha123");
        return "Bearer " + login(username, "senha123");
    }

    protected Long criarCategoria(String auth, String nome) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/categorias")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "%s"}
                                """.formatted(nome)))
                .andExpect(status().isCreated())
                .andReturn();
        return lerId(resultado);
    }

    protected Long criarTransacao(String auth, String descricao, Long categoriaId) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson(descricao, "50.00", "2026-11-25", "DESPESA", categoriaId)))
                .andExpect(status().isCreated())
                .andReturn();
        return lerId(resultado);
    }

    protected String transacaoJson(String descricao, String valor, String data, String tipo, Long categoriaId) {
        return """
                {"descricao": "%s", "valor": %s, "data": "%s", "tipo": "%s", "categoriaId": %d}
                """.formatted(descricao, valor, data, tipo, categoriaId);
    }

    protected Long lerId(MvcResult resultado) throws Exception {
        Number id = JsonPath.read(corpo(resultado), "$.id");
        return id.longValue();
    }

    protected String lerCampo(MvcResult resultado, String caminho) throws Exception {
        return JsonPath.read(corpo(resultado), caminho);
    }

    // O corpo vem em UTF-8; lendo assim, as mensagens com acento chegam certas.
    private String corpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
