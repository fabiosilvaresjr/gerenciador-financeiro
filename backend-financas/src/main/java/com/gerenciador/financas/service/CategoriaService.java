package com.gerenciador.financas.service;

import com.gerenciador.financas.dto.CategoriaRequestDTO;
import com.gerenciador.financas.dto.CategoriaResponseDTO;
import com.gerenciador.financas.model.Categoria;
import com.gerenciador.financas.model.Usuario;
import com.gerenciador.financas.repository.CategoriaRepository;
import com.gerenciador.financas.repository.TransacaoRepository;
import com.gerenciador.financas.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Todos os métodos recebem o ID do usuário logado (vindo do token) e só trabalham com as
 * categorias dele. A categoria de outro usuário é tratada como inexistente (404), para a API
 * não revelar que aquele ID existe.
 */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    /** Categorias que todo usuário novo ganha no cadastro (lista definida em 2026-11-26). */
    public static final List<String> CATEGORIAS_PADRAO = List.of(
            "Alimentação", "Moradia", "Transporte", "Saúde", "Educação", "Lazer", "Salário", "Outros");

    /**
     * Nome da categoria que todo usuário também ganha no cadastro, além das padrão. Recebe as
     * transações criadas sem categoria e não pode ser arquivada, renomeada nem excluída.
     */
    public static final String SEM_CATEGORIA = "Sem categoria";

    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    /** Por padrão, só as categorias ativas. Com incluirArquivadas = true, também as arquivadas. */
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar(Long usuarioId, boolean incluirArquivadas) {
        List<Categoria> categorias = incluirArquivadas
                ? categoriaRepository.findByUsuarioId(usuarioId)
                : categoriaRepository.findByUsuarioIdAndArquivadaFalse(usuarioId);
        return categorias.stream()
                .map(CategoriaResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoriaResponseDTO buscarPorId(Long id, Long usuarioId) {
        return CategoriaResponseDTO.fromEntity(buscarDoUsuario(id, usuarioId));
    }

    @Transactional
    public CategoriaResponseDTO criar(CategoriaRequestDTO dto, Long usuarioId) {
        Categoria categoria = Categoria.builder()
                .nome(dto.getNome())
                // getReferenceById não faz SELECT: cria uma referência ao usuário só para gravar o usuario_id.
                .usuario(usuarioRepository.getReferenceById(usuarioId))
                .build();
        Categoria salva = categoriaRepository.save(categoria);
        return CategoriaResponseDTO.fromEntity(salva);
    }

    /**
     * Cria as categorias padrão e a "Sem categoria" do usuário recém-cadastrado. É chamado pelo
     * AuthService.registrar e entra na mesma transação dele (propagação padrão REQUIRED): se algo
     * falhar aqui, o cadastro do usuário também é desfeito, e nunca fica um usuário sem as categorias.
     */
    @Transactional
    public void criarCategoriasPadrao(Usuario usuario) {
        List<Categoria> categorias = new ArrayList<>();
        for (String nome : CATEGORIAS_PADRAO) {
            categorias.add(Categoria.builder().nome(nome).usuario(usuario).build());
        }
        categorias.add(Categoria.builder().nome(SEM_CATEGORIA).semCategoria(true).usuario(usuario).build());
        categoriaRepository.saveAll(categorias);
    }

    @Transactional
    public CategoriaResponseDTO atualizar(Long id, CategoriaRequestDTO dto, Long usuarioId) {
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        if (categoria.isSemCategoria()) {
            throw conflito("A categoria \"" + SEM_CATEGORIA + "\" não pode ser renomeada.");
        }
        categoria.setNome(dto.getNome());
        Categoria atualizada = categoriaRepository.save(categoria);
        return CategoriaResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        // Primeiro confere o dono: a categoria de outro usuário responde 404, nunca o 409 abaixo
        // (o 409 revelaria que a categoria existe e tem transações).
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        if (categoria.isSemCategoria()) {
            throw conflito("A categoria \"" + SEM_CATEGORIA + "\" não pode ser excluída.");
        }
        if (transacaoRepository.existsByCategoriaId(id)) {
            throw conflito("A categoria possui transações e não pode ser excluída. Arquive a categoria em vez de excluí-la.");
        }
        categoriaRepository.delete(categoria);
    }

    /** Arquivar uma categoria já arquivada não é erro: ela só continua arquivada (idempotente). */
    @Transactional
    public CategoriaResponseDTO arquivar(Long id, Long usuarioId) {
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        if (categoria.isSemCategoria()) {
            throw conflito("A categoria \"" + SEM_CATEGORIA + "\" não pode ser arquivada.");
        }
        categoria.setArquivada(true);
        return CategoriaResponseDTO.fromEntity(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponseDTO desarquivar(Long id, Long usuarioId) {
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        categoria.setArquivada(false);
        return CategoriaResponseDTO.fromEntity(categoriaRepository.save(categoria));
    }

    /** Busca a categoria entre as do usuário (de outro usuário → 404, como se não existisse). */
    @Transactional(readOnly = true)
    public Categoria buscarDoUsuario(Long id, Long usuarioId) {
        return categoriaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada com o ID: " + id));
    }

    /**
     * Categoria para uma transação nova (ou para trocar a categoria de uma transação). Usado pelo
     * TransacaoService.
     * - Sem ID: a "Sem categoria" do usuário.
     * - Com ID: a categoria tem que ser do usuário (senão 404) e não pode estar arquivada (409).
     */
    @Transactional(readOnly = true)
    public Categoria buscarParaTransacao(Long id, Long usuarioId) {
        if (id == null) {
            return categoriaRepository.findByUsuarioIdAndSemCategoriaTrue(usuarioId)
                    // Todo usuário tem a sua (cadastro ou migration V5); se faltar, é erro do sistema.
                    .orElseThrow(() -> new IllegalStateException(
                            "Usuário " + usuarioId + " sem a categoria \"" + SEM_CATEGORIA + "\""));
        }
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        if (categoria.isArquivada()) {
            throw conflito("A categoria está arquivada e não pode ser usada em transações novas. "
                    + "Desarquive a categoria para usá-la.");
        }
        return categoria;
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
    }
}
