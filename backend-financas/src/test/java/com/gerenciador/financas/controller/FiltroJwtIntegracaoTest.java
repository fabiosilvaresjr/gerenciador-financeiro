package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Casos do filtro JWT (AuthTokenFilter + JwtUtils) que os testes do AuthIntegracaoTest não cobrem.
 *
 * Em todos, a requisição tem que cair no AuthEntryPointJwt: 401 com o corpo JSON padrão.
 * Os tokens "ruins" são montados aqui com o jjwt, do mesmo jeito que o JwtUtils monta os bons.
 */
class FiltroJwtIntegracaoTest extends IntegracaoTestBase {

    private static final String ROTA_PROTEGIDA = "/api/categorias";

    // O mesmo segredo que a IntegracaoTestBase configura para a aplicação nos testes.
    @Value("${financas.app.jwtSecret}")
    private String segredoDaAplicacao;

    @Test
    void tokenExpiradoResponde401() throws Exception {
        registrarELogar("maria");
        // Assinado com a chave certa e para um usuário que existe: o único problema é a data.
        long agora = System.currentTimeMillis();
        String tokenExpirado = Jwts.builder()
                .setSubject("maria")
                .setIssuedAt(new Date(agora - 120_000))
                .setExpiration(new Date(agora - 60_000)) // venceu há 1 minuto
                .signWith(Keys.hmacShaKeyFor(segredoDaAplicacao.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        conferir401(mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + tokenExpirado)));
    }

    @Test
    void tokenAssinadoComOutraChaveResponde401() throws Exception {
        registrarELogar("maria");
        // Mesmo formato e usuário que existe, mas assinado com um segredo que a aplicação não conhece
        // (é o que alguém faria para forjar um token).
        String outroSegredo = "outro-segredo-que-a-aplicacao-nao-conhece-0123456789";
        String tokenForjado = Jwts.builder()
                .setSubject("maria")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(outroSegredo.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        conferir401(mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Bearer " + tokenForjado)));
    }

    @Test
    void tokenValidoDeUsuarioQueNaoExisteMaisResponde401() throws Exception {
        String auth = registrarELogar("maria");

        // Apaga a maria direto no banco (a API não tem exclusão de usuário). O token continua
        // com assinatura e data válidas, mas o filtro não encontra mais o usuário.
        // Os dados dela saem antes, por causa das FKs (o cadastro pode ter criado categorias
        // e o login criou um refresh token).
        jdbcTemplate.update("DELETE FROM refresh_token WHERE usuario_id = (SELECT id FROM usuarios WHERE username = 'maria')");
        jdbcTemplate.update("DELETE FROM transacao WHERE usuario_id = (SELECT id FROM usuarios WHERE username = 'maria')");
        jdbcTemplate.update("DELETE FROM categoria WHERE usuario_id = (SELECT id FROM usuarios WHERE username = 'maria')");
        jdbcTemplate.update("DELETE FROM usuario_roles WHERE usuario_id = (SELECT id FROM usuarios WHERE username = 'maria')");
        jdbcTemplate.update("DELETE FROM usuarios WHERE username = 'maria'");

        conferir401(mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", auth)));
    }

    @Test
    void tokenValidoSemOPrefixoBearerResponde401() throws Exception {
        // "Bearer eyJ..." → só "eyJ...". O token em si é válido.
        String tokenSemPrefixo = registrarELogar("maria").substring("Bearer ".length());

        conferir401(mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", tokenSemPrefixo)));
    }

    @Test
    void headerBasicComCredenciaisCorretasResponde401() throws Exception {
        // Usuário e senha certos, mas no esquema Basic: a API só aceita "Bearer <token>".
        registrarELogar("maria");
        String basic = Base64.getEncoder().encodeToString("maria:senha123".getBytes(StandardCharsets.UTF_8));

        conferir401(mockMvc.perform(get(ROTA_PROTEGIDA).header("Authorization", "Basic " + basic)));
    }

    // Corpo montado pelo AuthEntryPointJwt.
    private void conferir401(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Não autorizado: token ausente, inválido ou expirado"))
                .andExpect(jsonPath("$.path").value(ROTA_PROTEGIDA))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
