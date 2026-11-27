package com.gerenciador.financas.controller;

import com.gerenciador.financas.IntegracaoTestBase;
import com.gerenciador.financas.service.CategoriaService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Categorias padrão criadas no cadastro (POST /api/auth/register).
 */
class CategoriasPadraoIntegracaoTest extends IntegracaoTestBase {

    // "Espião": o CategoriaService de verdade, mas que um teste pode mandar falhar de propósito.
    // Sem configuração (nos outros testes), ele se comporta normalmente. O Mockito o reinicia
    // depois de cada teste.
    @MockitoSpyBean
    private CategoriaService categoriaService;

    @Test
    void cadastroCriaAsCategoriasPadraoDoNovoUsuario() throws Exception {
        String auth = registrarELogar("maria");

        mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].nome", containsInAnyOrder(
                        "Alimentação", "Moradia", "Transporte", "Saúde", "Educação", "Lazer", "Salário", "Outros")));
    }

    @Test
    void cadaUsuarioGanhaAsPropriasCategoriasPadrao() throws Exception {
        registrarELogar("ana");
        registrarELogar("bruno");

        // Não são categorias compartilhadas: são 8 linhas para cada usuário.
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categoria", Integer.class);
        Integer donos = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT usuario_id) FROM categoria", Integer.class);
        assertThat(total).isEqualTo(16);
        assertThat(donos).isEqualTo(2);
    }

    @Test
    void categoriaPadraoPodeSerUsadaNumaTransacao() throws Exception {
        String auth = registrarELogar("maria");
        Long alimentacao = idDaCategoria(auth, "Alimentação");

        mockMvc.perform(post("/api/transacoes")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transacaoJson("Almoço", "35.00", "2026-11-27", "DESPESA", alimentacao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria.nome").value("Alimentação"));
    }

    @Test
    void cadastroRecusadoNaoCriaCategorias() throws Exception {
        registrar("maria", "maria@exemplo.com", "senha123");

        // Mesmo username: 409, e nenhuma categoria a mais além das 8 da primeira maria.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "maria", "email": "outra@exemplo.com", "password": "senha123"}
                                """))
                .andExpect(status().isConflict());

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categoria", Integer.class);
        assertThat(total).isEqualTo(8);
    }

    @Test
    void seCriarAsCategoriasFalharOUsuarioTambemNaoEGravado() {
        // Simula uma falha no meio do cadastro, depois de o usuário já ter sido inserido.
        doThrow(new IllegalStateException("Falha simulada ao criar as categorias padrão"))
                .when(categoriaService).criarCategoriasPadrao(any());

        // Sem handler para essa exceção, o MockMvc a repassa para o teste.
        assertThatThrownBy(() -> registrar("maria", "maria@exemplo.com", "senha123"))
                .hasRootCauseMessage("Falha simulada ao criar as categorias padrão");

        // O rollback da transação do registrar desfez o INSERT do usuário (e dos papéis dele).
        Integer usuarios = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class);
        Integer papeis = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usuario_roles", Integer.class);
        assertThat(usuarios).isZero();
        assertThat(papeis).isZero();
    }

    private Long idDaCategoria(String auth, String nome) throws Exception {
        String corpo = mockMvc.perform(get("/api/categorias").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Filtro do JsonPath: os "id" dos itens cujo "nome" é o procurado.
        List<Number> ids = JsonPath.read(corpo, "$[?(@.nome == '" + nome + "')].id");
        assertThat(ids).hasSize(1);
        return ids.get(0).longValue();
    }
}
