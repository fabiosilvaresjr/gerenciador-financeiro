package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento entre usuários: a Ana (usuário A) tem uma categoria e uma transação, e o Bruno
 * (usuário B) tenta listar, ler, alterar e apagar esses dados com o próprio token.
 *
 * A resposta esperada é sempre 404, a mesma de um ID que não existe, para a API não revelar
 * que o recurso existe. Depois de cada tentativa, o teste confere com o token da Ana que os
 * dados dela continuam iguais.
 */
class IsolamentoIntegracaoTest extends IntegracaoTestBase {

    private String authAna;
    private String authBruno;
    private Long categoriaDaAna;
    private Long transacaoDaAna;
    private Long categoriaDoBruno;

    @BeforeEach
    void prepararDadosDosDoisUsuarios() throws Exception {
        authAna = registrarELogar("ana");
        authBruno = registrarELogar("bruno");

        categoriaDaAna = criarCategoria(authAna, "Mercado da Ana");
        transacaoDaAna = criarTransacao(authAna, "Compras da Ana", categoriaDaAna);

        categoriaDoBruno = criarCategoria(authBruno, "Mercado do Bruno");
    }

    // ---------- Categorias ----------

    @Test
    void listagemDeCategoriasMostraSoAsDoProprioUsuario() throws Exception {
        // Cada um vê as próprias 8 categorias padrão + a que criou (as padrão do outro não aparecem).
        int esperado = CATEGORIAS_DO_CADASTRO + 1;

        mockMvc.perform(get("/api/categorias").header("Authorization", authBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(esperado)))
                .andExpect(jsonPath("$[*].nome", hasItem("Mercado do Bruno")))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Mercado da Ana"))));

        mockMvc.perform(get("/api/categorias").header("Authorization", authAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(esperado)))
                .andExpect(jsonPath("$[*].nome", hasItem("Mercado da Ana")))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Mercado do Bruno"))));
    }

    @Test
    void buscarCategoriaDeOutroUsuarioResponde404() throws Exception {
        // Mesma mensagem de uma categoria inexistente: o Bruno não descobre que o ID existe.
        mockMvc.perform(get("/api/categorias/{id}", categoriaDaAna).header("Authorization", authBruno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: " + categoriaDaAna));
    }

    @Test
    void atualizarCategoriaDeOutroUsuarioResponde404ENaoAltera() throws Exception {
        mockMvc.perform(put("/api/categorias/{id}", categoriaDaAna)
                        .header("Authorization", authBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Alterada pelo Bruno"}
                                """))
                .andExpect(status().isNotFound());

        conferirCategoriaDaAnaIntacta();
    }

    @Test
    void excluirCategoriaDeOutroUsuarioResponde404ENaoApaga() throws Exception {
        // Categoria sem transações: se o dono não fosse conferido, o DELETE apagaria (204).
        Long categoriaVaziaDaAna = criarCategoria(authAna, "Lazer da Ana");

        mockMvc.perform(delete("/api/categorias/{id}", categoriaVaziaDaAna).header("Authorization", authBruno))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/categorias/{id}", categoriaVaziaDaAna).header("Authorization", authAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Lazer da Ana"));
    }

    @Test
    void excluirCategoriaComTransacoesDeOutroUsuarioResponde404EnaoO409() throws Exception {
        // O 409 ("possui transações") revelaria que a categoria existe; o dono é conferido antes.
        mockMvc.perform(delete("/api/categorias/{id}", categoriaDaAna).header("Authorization", authBruno))
                .andExpect(status().isNotFound());

        conferirCategoriaDaAnaIntacta();
        conferirTransacaoDaAnaIntacta();
    }

    // ---------- Transações ----------

    @Test
    void listagemDeTransacoesMostraSoAsDoProprioUsuario() throws Exception {
        criarTransacao(authBruno, "Compras do Bruno", categoriaDoBruno);

        mockMvc.perform(get("/api/transacoes").header("Authorization", authBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descricao").value("Compras do Bruno"));

        mockMvc.perform(get("/api/transacoes").header("Authorization", authAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descricao").value("Compras da Ana"));
    }

    @Test
    void buscarTransacaoDeOutroUsuarioResponde404() throws Exception {
        mockMvc.perform(get("/api/transacoes/{id}", transacaoDaAna).header("Authorization", authBruno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transação não encontrada com o ID: " + transacaoDaAna));
    }

    @Test
    void atualizarTransacaoDeOutroUsuarioResponde404ENaoAltera() throws Exception {
        // O corpo é válido e usa uma categoria do próprio Bruno: o único motivo do 404 é o dono.
        mockMvc.perform(put("/api/transacoes/{id}", transacaoDaAna)
                        .header("Authorization", authBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Alterada pelo Bruno", "1.00", "2026-01-01", "RECEITA", categoriaDoBruno)))
                .andExpect(status().isNotFound());

        conferirTransacaoDaAnaIntacta();
    }

    @Test
    void excluirTransacaoDeOutroUsuarioResponde404ENaoApaga() throws Exception {
        mockMvc.perform(delete("/api/transacoes/{id}", transacaoDaAna).header("Authorization", authBruno))
                .andExpect(status().isNotFound());

        conferirTransacaoDaAnaIntacta();
    }

    @Test
    void criarTransacaoComCategoriaDeOutroUsuarioResponde404ENaoGrava() throws Exception {
        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", authBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Usando a categoria da Ana", "10.00", "2026-11-25", "DESPESA", categoriaDaAna)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: " + categoriaDaAna));

        // Conferido no banco, sem o filtro por usuário da API: só existe a transação da Ana.
        Integer totalDeTransacoes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transacao", Integer.class);
        assertThat(totalDeTransacoes).isEqualTo(1);
        conferirTransacaoDaAnaIntacta();
    }

    @Test
    void moverPropriaTransacaoParaCategoriaDeOutroUsuarioResponde404ENaoAltera() throws Exception {
        Long transacaoDoBruno = criarTransacao(authBruno, "Compras do Bruno", categoriaDoBruno);

        mockMvc.perform(put("/api/transacoes/{id}", transacaoDoBruno)
                        .header("Authorization", authBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras do Bruno", "50.00", "2026-11-25", "DESPESA", categoriaDaAna)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: " + categoriaDaAna));

        mockMvc.perform(get("/api/transacoes/{id}", transacaoDoBruno).header("Authorization", authBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(categoriaDoBruno));
    }

    // ---------- Conferências com o token da Ana ----------

    private void conferirCategoriaDaAnaIntacta() throws Exception {
        mockMvc.perform(get("/api/categorias/{id}", categoriaDaAna).header("Authorization", authAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Mercado da Ana"));
    }

    // Valores criados pelo atalho criarTransacao da base: 50.00, 2026-11-25, DESPESA.
    private void conferirTransacaoDaAnaIntacta() throws Exception {
        mockMvc.perform(get("/api/transacoes/{id}", transacaoDaAna).header("Authorization", authAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Compras da Ana"))
                .andExpect(jsonPath("$.valor").value(50.00))
                .andExpect(jsonPath("$.data").value("2026-11-25"))
                .andExpect(jsonPath("$.tipo").value("DESPESA"))
                .andExpect(jsonPath("$.categoria.id").value(categoriaDaAna));
    }
}
