package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh token: login, rotação a cada uso, detecção de reuso, logout, tempos de vida e
 * armazenamento só do hash.
 *
 * Vocabulário: cada login abre uma "sessão" (família de refresh tokens). R1, R2... são os
 * refresh tokens de uma mesma sessão, na ordem em que foram emitidos.
 */
class RefreshTokenIntegracaoTest extends IntegracaoTestBase {

    private static final String MENSAGEM_INVALIDO = "Refresh token inválido ou expirado";

    @Value("${financas.app.jwtSecret}")
    private String segredoDaAplicacao;

    /** O par de tokens que o login e o refresh devolvem. */
    private record Tokens(String accessToken, String refreshToken) {
    }

    // ---------- Login ----------

    @Test
    void loginDevolveAccessTokenERefreshToken() throws Exception {
        registrar("maria", "maria@exemplo.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "maria", "password": "senha123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("maria"));
    }

    // ---------- Armazenamento e tempos de vida ----------

    @Test
    void bancoGuardaSoOHashDoRefreshToken() throws Exception {
        Tokens tokens = cadastrarELogar("maria");

        String hashGravado = jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_token", String.class);
        assertThat(hashGravado).isEqualTo(sha256(tokens.refreshToken()));

        // A linha inteira convertida em texto não contém o token puro em nenhuma coluna.
        Integer linhasComOTokenPuro = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token r WHERE CAST(r AS TEXT) LIKE '%' || ? || '%'",
                Integer.class, tokens.refreshToken());
        assertThat(linhasComOTokenPuro).isZero();
    }

    @Test
    void accessTokenVale15Minutos() throws Exception {
        Claims claims = lerClaims(cadastrarELogar("maria").accessToken());

        long segundos = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        // As datas do JWT são em segundos e o JwtUtils lê o relógio duas vezes: pode dar 1 s a mais.
        assertThat(segundos).isCloseTo(15 * 60, within(1L));
    }

    @Test
    void refreshTokenVale7Dias() throws Exception {
        cadastrarELogar("maria");

        BigDecimal segundos = jdbcTemplate.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (expira_em - criado_em)) FROM refresh_token", BigDecimal.class);
        assertThat(segundos).isEqualByComparingTo(BigDecimal.valueOf(7 * 24 * 60 * 60));
    }

    // ---------- Refresh e rotação ----------

    @Test
    void refreshDevolveTokensNovosEOAccessNovoFunciona() throws Exception {
        Tokens r1 = cadastrarELogar("maria");

        Tokens r2 = refreshComSucesso(r1.refreshToken());

        assertThat(r2.refreshToken()).isNotEqualTo(r1.refreshToken());
        assertThat(r2.accessToken()).isNotBlank();
        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + r2.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenJaUsadoNaoFuncionaDeNovo() throws Exception {
        Tokens r1 = cadastrarELogar("maria");
        refreshComSucesso(r1.refreshToken());

        conferir401(refresh(r1.refreshToken()));
    }

    @Test
    void reusoDeRefreshTokenRevogaASessaoInteira() throws Exception {
        Tokens r1 = cadastrarELogar("maria");
        Tokens r2 = refreshComSucesso(r1.refreshToken());

        // R1 reaparece: reuso. Responde 401...
        conferir401(refresh(r1.refreshToken()));
        // ...e R2, que ainda não tinha sido usado, também foi revogado (a revogação não foi desfeita
        // pelo rollback da exceção).
        conferir401(refresh(r2.refreshToken()));

        Integer revogados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE revogado_em IS NOT NULL", Integer.class);
        assertThat(revogados).isEqualTo(2);
    }

    @Test
    void reusoNumaSessaoNaoDerrubaOutraSessao() throws Exception {
        Tokens notebook = cadastrarELogar("maria");
        Tokens celular = logar("maria");

        refreshComSucesso(notebook.refreshToken());
        conferir401(refresh(notebook.refreshToken())); // reuso no notebook

        refreshComSucesso(celular.refreshToken());     // o celular continua funcionando
    }

    @Test
    void refreshTokenVencidoResponde401() throws Exception {
        Tokens tokens = cadastrarELogar("maria");
        jdbcTemplate.update("UPDATE refresh_token SET expira_em = now() - INTERVAL '1 minute' WHERE token_hash = ?",
                sha256(tokens.refreshToken()));

        conferir401(refresh(tokens.refreshToken()));
    }

    @Test
    void refreshTokenDesconhecidoResponde401() throws Exception {
        conferir401(refresh("token-que-nunca-existiu"));
    }

    @Test
    void refreshELogoutSemTokenRespondem400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo", hasItem("refreshToken")))
                .andExpect(jsonPath("$.campos[*].mensagem", hasItem("O refresh token é obrigatório")));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo", hasItem("refreshToken")));
    }

    // ---------- Logout ----------

    @Test
    void logoutResponde204EDepoisORefreshNaoFunciona() throws Exception {
        Tokens tokens = cadastrarELogar("maria");

        logout(tokens.refreshToken());

        conferir401(refresh(tokens.refreshToken()));
    }

    @Test
    void logoutRevogaTodosOsTokensDaSessao() throws Exception {
        Tokens r1 = cadastrarELogar("maria");
        Tokens r2 = refreshComSucesso(r1.refreshToken());

        logout(r2.refreshToken());

        conferir401(refresh(r1.refreshToken()));
        conferir401(refresh(r2.refreshToken()));
        Integer ativos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE revogado_em IS NULL", Integer.class);
        assertThat(ativos).isZero();
    }

    @Test
    void logoutComTokenDesconhecidoOuRepetidoResponde204() throws Exception {
        Tokens tokens = cadastrarELogar("maria");

        logout("token-que-nunca-existiu");
        logout(tokens.refreshToken());
        logout(tokens.refreshToken()); // segunda vez: mesmo resultado
    }

    @Test
    void logoutNumaSessaoNaoDerrubaOutraSessao() throws Exception {
        Tokens notebook = cadastrarELogar("maria");
        Tokens celular = logar("maria");

        logout(notebook.refreshToken());

        refreshComSucesso(celular.refreshToken());
    }

    // ---------- Dono dos dados ----------

    @Test
    void refreshDaAnaGeraAccessTokenDaAna() throws Exception {
        Tokens ana = cadastrarELogar("ana");
        Tokens bruno = cadastrarELogar("bruno");
        criarCategoria("Bearer " + ana.accessToken(), "Mercado da Ana");
        criarCategoria("Bearer " + bruno.accessToken(), "Mercado do Bruno");

        Tokens anaDepoisDoRefresh = refreshComSucesso(ana.refreshToken());

        assertThat(lerClaims(anaDepoisDoRefresh.accessToken()).getSubject()).isEqualTo("ana");
        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + anaDepoisDoRefresh.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Mercado da Ana")))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Mercado do Bruno"))));

        // O token novo foi gravado com a Ana como dona.
        String dono = jdbcTemplate.queryForObject(
                "SELECT u.username FROM refresh_token r JOIN usuarios u ON u.id = r.usuario_id WHERE r.token_hash = ?",
                String.class, sha256(anaDepoisDoRefresh.refreshToken()));
        assertThat(dono).isEqualTo("ana");
    }

    // ---------- Concorrência ----------

    @Test
    void doisRefreshesAoMesmoTempoComOMesmoTokenSoUmFunciona() throws Exception {
        Tokens tokens = cadastrarELogar("maria");

        // As duas threads esperam o "largada" e chamam o refresh juntas.
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Integer> pedirRefresh = () -> {
            largada.await();
            return refresh(tokens.refreshToken()).andReturn().getResponse().getStatus();
        };
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> primeira = threads.submit(pedirRefresh);
            Future<Integer> segunda = threads.submit(pedirRefresh);
            largada.countDown();

            // Uma troca o token; a outra chega com o token já usado (reuso) e recebe 401.
            assertThat(List.of(primeira.get(), segunda.get())).containsExactlyInAnyOrder(200, 401);
        } finally {
            threads.shutdown();
        }
    }

    // ---------- Atalhos ----------

    private Tokens cadastrarELogar(String username) throws Exception {
        registrar(username, username + "@exemplo.com", "senha123");
        return logar(username);
    }

    private Tokens logar(String username) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "senha123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return lerTokens(resultado);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private Tokens refreshComSucesso(String refreshToken) throws Exception {
        MvcResult resultado = refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andReturn();
        return lerTokens(resultado);
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());
    }

    // Sempre a mesma mensagem, qualquer que seja o motivo da recusa.
    private void conferir401(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(MENSAGEM_INVALIDO))
                .andExpect(jsonPath("$.path").value("/api/auth/refresh"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    private Tokens lerTokens(MvcResult resultado) throws Exception {
        String corpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Tokens(JsonPath.read(corpo, "$.token"), JsonPath.read(corpo, "$.refreshToken"));
    }

    private Claims lerClaims(String accessToken) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(segredoDaAplicacao.getBytes()))
                .build()
                .parseClaimsJws(accessToken)
                .getBody();
    }

    // Calculado aqui de forma independente do RefreshTokenService, para o teste não depender dele.
    private static String sha256(String valor) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
