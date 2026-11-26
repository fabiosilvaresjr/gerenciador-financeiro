package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegracaoTest extends IntegracaoTestBase {

    @Test
    void cadastroCriaUsuarioComRoleUserENaoDevolveSenha() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "maria", "email": "maria@exemplo.com", "password": "senha123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("maria"))
                .andExpect(jsonPath("$.email").value("maria@exemplo.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void cadastroComUsernameRepetidoResponde409() throws Exception {
        registrar("maria", "maria@exemplo.com", "senha123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "maria", "email": "outro@exemplo.com", "password": "senha123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username já está em uso"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void cadastroComEmailRepetidoResponde409() throws Exception {
        registrar("maria", "maria@exemplo.com", "senha123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "outra", "email": "maria@exemplo.com", "password": "senha123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email já está em uso"));
    }

    @Test
    void cadastroComDadosInvalidosResponde400ComOsCampos() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "", "email": "nao-e-email", "password": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dados inválidos"))
                // A ordem da lista não é garantida, por isso só conferimos que cada campo aparece
                .andExpect(jsonPath("$.campos[*].campo", hasItem("username")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("email")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("password")))
                .andExpect(jsonPath("$.campos[*].mensagem", hasItem("O email informado é inválido")));
    }

    @Test
    void loginComCredenciaisCorretasDevolveToken() throws Exception {
        registrar("maria", "maria@exemplo.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "maria", "password": "senha123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.username").value("maria"));
    }

    @Test
    void loginComSenhaErradaResponde401() throws Exception {
        registrar("maria", "maria@exemplo.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "maria", "password": "errada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Username ou senha inválidos"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void loginDeUsuarioInexistenteResponde401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ninguem", "password": "senha123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Username ou senha inválidos"));
    }

    @Test
    void rotaProtegidaSemTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Não autorizado: token ausente, inválido ou expirado"))
                .andExpect(jsonPath("$.path").value("/api/categorias"));
    }

    @Test
    void rotaProtegidaComTokenInvalidoResponde401() throws Exception {
        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer token-que-nao-vale"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegidaComTokenValidoResponde200() throws Exception {
        String auth = registrarELogar("maria");

        mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
