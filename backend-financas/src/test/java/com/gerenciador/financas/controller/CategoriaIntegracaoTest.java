package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import com.gerenciador.financas.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoriaIntegracaoTest extends IntegracaoTestBase {

    private String auth;

    @BeforeEach
    void logar() throws Exception {
        auth = registrarELogar("maria");
    }

    @Test
    void criaCategoria() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Mercado"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Mercado"));
    }

    @Test
    void listaCategorias() throws Exception {
        criarCategoria(auth, "Mercado");
        criarCategoria(auth, "Viagem");

        // As 8 categorias padrão do cadastro + as 2 criadas aqui.
        mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CategoriaService.CATEGORIAS_PADRAO.size() + 2)))
                .andExpect(jsonPath("$[*].nome", hasItems("Mercado", "Viagem")));
    }

    @Test
    void buscaCategoriaPorId() throws Exception {
        Long id = criarCategoria(auth, "Mercado");

        mockMvc.perform(get("/api/categorias/{id}", id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value("Mercado"));
    }

    @Test
    void atualizaCategoria() throws Exception {
        Long id = criarCategoria(auth, "Mercado");

        mockMvc.perform(put("/api/categorias/{id}", id)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Supermercado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Supermercado"));

        mockMvc.perform(get("/api/categorias/{id}", id).header("Authorization", auth))
                .andExpect(jsonPath("$.nome").value("Supermercado"));
    }

    @Test
    void excluiCategoriaSemTransacoes() throws Exception {
        Long id = criarCategoria(auth, "Mercado");

        mockMvc.perform(delete("/api/categorias/{id}", id).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categorias/{id}", id).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirCategoriaComTransacoesResponde409EMantemAsTransacoes() throws Exception {
        Long categoriaId = criarCategoria(auth, "Mercado");
        Long transacaoId = criarTransacao(auth, "Compras da semana", categoriaId);

        mockMvc.perform(delete("/api/categorias/{id}", categoriaId).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "A categoria possui transações e não pode ser excluída. Arquive a categoria em vez de excluí-la."));

        // A categoria e a transação continuam lá
        mockMvc.perform(get("/api/categorias/{id}", categoriaId).header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/transacoes/{id}", transacaoId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(categoriaId));
    }

    @Test
    void buscarCategoriaInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/categorias/{id}", 999999).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: 999999"))
                .andExpect(jsonPath("$.path").value("/api/categorias/999999"));
    }

    @Test
    void atualizarCategoriaInexistenteResponde404() throws Exception {
        mockMvc.perform(put("/api/categorias/{id}", 999999)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Qualquer"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirCategoriaInexistenteResponde404() throws Exception {
        mockMvc.perform(delete("/api/categorias/{id}", 999999).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarCategoriaSemNomeResponde400ComOCampo() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].campo").value("nome"))
                .andExpect(jsonPath("$.campos[0].mensagem").value("O nome da categoria é obrigatório"));
    }
}
