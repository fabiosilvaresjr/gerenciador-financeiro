package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransacaoIntegracaoTest extends IntegracaoTestBase {

    private String auth;
    private Long categoriaId;

    @BeforeEach
    void prepararUsuarioECategoria() throws Exception {
        auth = registrarELogar("maria");
        categoriaId = criarCategoria(auth, "Mercado");
    }

    @Test
    void criaTransacao() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras da semana", "123.45", "2026-11-25", "DESPESA", categoriaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.descricao").value("Compras da semana"))
                .andExpect(jsonPath("$.valor").value(123.45))
                .andExpect(jsonPath("$.data").value("2026-11-25"))
                .andExpect(jsonPath("$.tipo").value("DESPESA"))
                .andExpect(jsonPath("$.categoria.id").value(categoriaId))
                .andExpect(jsonPath("$.categoria.nome").value("Mercado"));
    }

    @Test
    void listaTransacoes() throws Exception {
        criarTransacao(auth, "Compras da semana", categoriaId);
        criarTransacao(auth, "Feira", categoriaId);

        mockMvc.perform(get("/api/transacoes").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void buscaTransacaoPorId() throws Exception {
        Long id = criarTransacao(auth, "Compras da semana", categoriaId);

        mockMvc.perform(get("/api/transacoes/{id}", id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.descricao").value("Compras da semana"));
    }

    @Test
    void atualizaTransacaoInclusiveACategoria() throws Exception {
        Long id = criarTransacao(auth, "Compras da semana", categoriaId);
        Long salarioId = criarCategoria(auth, "Salário");

        mockMvc.perform(put("/api/transacoes/{id}", id)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Salário de novembro", "5000.00", "2026-11-05", "RECEITA", salarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Salário de novembro"))
                .andExpect(jsonPath("$.valor").value(5000.00))
                .andExpect(jsonPath("$.data").value("2026-11-05"))
                .andExpect(jsonPath("$.tipo").value("RECEITA"))
                .andExpect(jsonPath("$.categoria.id").value(salarioId));
    }

    @Test
    void excluiTransacao() throws Exception {
        Long id = criarTransacao(auth, "Compras da semana", categoriaId);

        mockMvc.perform(delete("/api/transacoes/{id}", id).header("Authorization", auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transacoes/{id}", id).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscarTransacaoInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/transacoes/{id}", 999999).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transação não encontrada com o ID: 999999"))
                .andExpect(jsonPath("$.path").value("/api/transacoes/999999"));
    }

    @Test
    void atualizarTransacaoInexistenteResponde404() throws Exception {
        mockMvc.perform(put("/api/transacoes/{id}", 999999)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Qualquer", "10.00", "2026-11-25", "DESPESA", categoriaId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirTransacaoInexistenteResponde404() throws Exception {
        mockMvc.perform(delete("/api/transacoes/{id}", 999999).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarTransacaoComCategoriaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras da semana", "10.00", "2026-11-25", "DESPESA", 999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: 999999"));
    }

    @Test
    void criarTransacaoInvalidaResponde400ComOsCampos() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao": "", "valor": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("descricao")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("valor")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("data")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("tipo")))
                // categoriaId não entra: é opcional (sem ela, a transação vai para a "Sem categoria").
                .andExpect(jsonPath("$.campos[*].campo", not(hasItem("categoriaId"))))
                .andExpect(jsonPath("$.campos[*].mensagem", hasItem("O valor da transação deve ser positivo")));
    }
}
