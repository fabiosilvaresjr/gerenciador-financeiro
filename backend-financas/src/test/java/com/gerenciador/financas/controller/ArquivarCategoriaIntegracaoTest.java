package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arquivar e desarquivar categoria (PATCH /api/categorias/{id}/arquivar e /desarquivar).
 *
 * Uma categoria arquivada some da listagem padrão e não pode ser usada em transações novas,
 * mas as transações antigas continuam com ela.
 */
class ArquivarCategoriaIntegracaoTest extends IntegracaoTestBase {

    private static final String MENSAGEM_ARQUIVADA =
            "A categoria está arquivada e não pode ser usada em transações novas. Desarquive a categoria para usá-la.";

    private String auth;
    private Long mercado;

    @BeforeEach
    void prepararUsuarioECategoria() throws Exception {
        auth = registrarELogar("maria");
        mercado = criarCategoria(auth, "Mercado");
    }

    // ---------- Arquivar e desarquivar ----------

    @Test
    void arquivarResponde200EMarcaACategoria() throws Exception {
        arquivar(mercado)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mercado))
                .andExpect(jsonPath("$.nome").value("Mercado"))
                .andExpect(jsonPath("$.arquivada").value(true));

        // Buscar pelo ID continua funcionando: arquivar não é apagar.
        mockMvc.perform(get("/api/categorias/{id}", mercado).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivada").value(true));
    }

    @Test
    void categoriaArquivadaSomeDaListagemPadrao() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CATEGORIAS_DO_CADASTRO)))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Mercado"))));
    }

    @Test
    void listagemComIncluirArquivadasMostraTodas() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(get("/api/categorias").param("incluirArquivadas", "true").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CATEGORIAS_DO_CADASTRO + 1)))
                .andExpect(jsonPath("$[?(@.nome == 'Mercado')].arquivada", hasItem(true)));
    }

    @Test
    void arquivarDuasVezesNaoEErro() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        arquivar(mercado)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivada").value(true));
    }

    @Test
    void desarquivarVoltaACategoriaParaAListagem() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(patch("/api/categorias/{id}/desarquivar", mercado).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivada").value(false));

        mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Mercado")));
    }

    @Test
    void arquivarCategoriaInexistenteResponde404() throws Exception {
        arquivar(999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: 999999"));
    }

    @Test
    void arquivarOuDesarquivarCategoriaDeOutroUsuarioResponde404ENaoAltera() throws Exception {
        String authBruno = registrarELogar("bruno");

        mockMvc.perform(patch("/api/categorias/{id}/arquivar", mercado).header("Authorization", authBruno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Categoria não encontrada com o ID: " + mercado));
        conferirArquivada(mercado, false);

        // E ao contrário: a Maria arquiva, o Bruno tenta desarquivar.
        arquivar(mercado).andExpect(status().isOk());
        mockMvc.perform(patch("/api/categorias/{id}/desarquivar", mercado).header("Authorization", authBruno))
                .andExpect(status().isNotFound());
        conferirArquivada(mercado, true);
    }

    @Test
    void renomearCategoriaArquivadaContinuaPermitido() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(put("/api/categorias/{id}", mercado)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Supermercado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Supermercado"))
                .andExpect(jsonPath("$.arquivada").value(true));
    }

    @Test
    void excluirCategoriaArquivadaSemTransacoesContinuaPermitido() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(delete("/api/categorias/{id}", mercado).header("Authorization", auth))
                .andExpect(status().isNoContent());
    }

    // ---------- Arquivada x transações ----------

    @Test
    void criarTransacaoComCategoriaArquivadaResponde409ENaoGrava() throws Exception {
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras", "10.00", "2026-11-28", "DESPESA", mercado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(MENSAGEM_ARQUIVADA));

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transacao", Integer.class);
        assertThat(total).isZero();
    }

    @Test
    void transacaoAntigaContinuaComACategoriaArquivada() throws Exception {
        Long transacao = criarTransacao(auth, "Compras da semana", mercado);
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(get("/api/transacoes/{id}", transacao).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(mercado))
                .andExpect(jsonPath("$.categoria.arquivada").value(true));
    }

    @Test
    void editarTransacaoAntigaMantendoACategoriaArquivadaEPermitido() throws Exception {
        // Sem isso, uma transação de categoria arquivada não poderia mais ter o valor corrigido.
        Long transacao = criarTransacao(auth, "Compras da semana", mercado);
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(put("/api/transacoes/{id}", transacao)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras da semana", "75.00", "2026-11-25", "DESPESA", mercado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(75.00))
                .andExpect(jsonPath("$.categoria.id").value(mercado));
    }

    @Test
    void moverTransacaoParaCategoriaArquivadaResponde409ENaoAltera() throws Exception {
        Long lazer = criarCategoria(auth, "Lazer extra");
        Long transacao = criarTransacao(auth, "Cinema", lazer);
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(put("/api/transacoes/{id}", transacao)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Cinema", "50.00", "2026-11-25", "DESPESA", mercado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(MENSAGEM_ARQUIVADA));

        mockMvc.perform(get("/api/transacoes/{id}", transacao).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(lazer));
    }

    @Test
    void depoisDeDesarquivarACategoriaVoltaASerUsadaEmTransacoes() throws Exception {
        arquivar(mercado).andExpect(status().isOk());
        mockMvc.perform(patch("/api/categorias/{id}/desarquivar", mercado).header("Authorization", auth))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Compras", "10.00", "2026-11-28", "DESPESA", mercado)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria.id").value(mercado));
    }

    @Test
    void excluirCategoriaArquivadaComTransacoesContinuaRespondendo409() throws Exception {
        criarTransacao(auth, "Compras da semana", mercado);
        arquivar(mercado).andExpect(status().isOk());

        mockMvc.perform(delete("/api/categorias/{id}", mercado).header("Authorization", auth))
                .andExpect(status().isConflict());
        conferirArquivada(mercado, true);
    }

    // ---------- auxiliares ----------

    private ResultActions arquivar(Long categoriaId) throws Exception {
        return mockMvc.perform(patch("/api/categorias/{id}/arquivar", categoriaId).header("Authorization", auth));
    }

    // Conferido no banco, sem passar pela API.
    private void conferirArquivada(Long categoriaId, boolean esperado) {
        Boolean arquivada = jdbcTemplate.queryForObject(
                "SELECT arquivada FROM categoria WHERE id = ?", Boolean.class, categoriaId);
        assertThat(arquivada).isEqualTo(esperado);
    }
}
