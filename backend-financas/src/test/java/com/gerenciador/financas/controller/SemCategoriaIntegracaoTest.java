package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A categoria "Sem categoria" que todo usuário ganha no cadastro: recebe as transações criadas
 * sem categoria e não pode ser arquivada, renomeada nem excluída.
 */
class SemCategoriaIntegracaoTest extends IntegracaoTestBase {

    private String auth;
    private Long semCategoria;

    @BeforeEach
    void prepararUsuario() throws Exception {
        auth = registrarELogar("maria");
        semCategoria = idDaSemCategoria(auth);
    }

    // ---------- Transações sem categoria ----------

    @Test
    void transacaoCriadaSemCategoriaVaiParaASemCategoria() throws Exception {
        // Sem o campo categoriaId no JSON.
        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "Pix sem descrição", "valor": 20.00, "data": "2026-11-28", "tipo": "DESPESA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria.id").value(semCategoria))
                .andExpect(jsonPath("$.categoria.nome").value("Sem categoria"))
                .andExpect(jsonPath("$.categoria.semCategoria").value(true));
    }

    @Test
    void transacaoComCategoriaIdNuloTambemVaiParaASemCategoria() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Pix", "20.00", "2026-11-28", "DESPESA", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria.id").value(semCategoria));
    }

    @Test
    void editarTransacaoSemCategoriaIdMoveParaASemCategoria() throws Exception {
        Long mercado = criarCategoria(auth, "Mercado");
        Long transacao = criarTransacao(auth, "Compras", mercado);

        mockMvc.perform(put("/api/transacoes/{id}", transacao)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras", "50.00", "2026-11-25", "DESPESA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(semCategoria));
    }

    @Test
    void cadaUsuarioUsaAPropriaSemCategoria() throws Exception {
        String authBruno = registrarELogar("bruno");
        Long semCategoriaDoBruno = idDaSemCategoria(authBruno);
        assertThat(semCategoriaDoBruno).isNotEqualTo(semCategoria);

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", authBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Pix do Bruno", "5.00", "2026-11-28", "RECEITA", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria.id").value(semCategoriaDoBruno));
    }

    // ---------- O que não pode ----------

    @Test
    void semCategoriaNaoPodeSerArquivada() throws Exception {
        mockMvc.perform(patch("/api/categorias/{id}/arquivar", semCategoria).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("A categoria \"Sem categoria\" não pode ser arquivada."));

        mockMvc.perform(get("/api/categorias/{id}", semCategoria).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivada").value(false));
    }

    @Test
    void semCategoriaNaoPodeSerExcluidaMesmoSemTransacoes() throws Exception {
        mockMvc.perform(delete("/api/categorias/{id}", semCategoria).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A categoria \"Sem categoria\" não pode ser excluída."));

        mockMvc.perform(get("/api/categorias/{id}", semCategoria).header("Authorization", auth))
                .andExpect(status().isOk());
    }

    @Test
    void semCategoriaNaoPodeSerRenomeada() throws Exception {
        mockMvc.perform(put("/api/categorias/{id}", semCategoria)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Diversos"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A categoria \"Sem categoria\" não pode ser renomeada."));

        mockMvc.perform(get("/api/categorias/{id}", semCategoria).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Sem categoria"));
    }

    @Test
    void semCategoriaDeOutroUsuarioResponde404EnaoO409() throws Exception {
        // O dono é conferido antes: o 409 revelaria que o ID existe.
        String authBruno = registrarELogar("bruno");

        mockMvc.perform(delete("/api/categorias/{id}", semCategoria).header("Authorization", authBruno))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/categorias/{id}/arquivar", semCategoria).header("Authorization", authBruno))
                .andExpect(status().isNotFound());
    }

    // ---------- Garantias do banco (migration V5) ----------

    @Test
    void bancoRecusaUmaSegundaSemCategoriaParaOMesmoUsuario() {
        Long usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE username = 'maria'", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO categoria (nome, usuario_id, sem_categoria) VALUES ('Outra sem categoria', ?, true)", usuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_categoria_sem_categoria_por_usuario");
    }

    @Test
    void bancoRecusaArquivarASemCategoria() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE categoria SET arquivada = true WHERE id = ?", semCategoria))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_categoria_sem_categoria_nao_arquivada");
    }

    // ---------- auxiliares ----------

    private Long idDaSemCategoria(String auth) throws Exception {
        String corpo = mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Number> ids = JsonPath.read(corpo, "$[?(@.semCategoria == true)].id");
        assertThat(ids).hasSize(1);
        return ids.get(0).longValue();
    }
}
