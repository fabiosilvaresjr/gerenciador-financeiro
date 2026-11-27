package com.gerenciador.financas.service;

import com.gerenciador.financas.dto.CategoriaRequestDTO;
import com.gerenciador.financas.dto.CategoriaResponseDTO;
import com.gerenciador.financas.model.Categoria;
import com.gerenciador.financas.repository.CategoriaRepository;
import com.gerenciador.financas.repository.TransacaoRepository;
import com.gerenciador.financas.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar(Long usuarioId) {
        return categoriaRepository.findByUsuarioId(usuarioId).stream()
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

    @Transactional
    public CategoriaResponseDTO atualizar(Long id, CategoriaRequestDTO dto, Long usuarioId) {
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        categoria.setNome(dto.getNome());
        Categoria atualizada = categoriaRepository.save(categoria);
        return CategoriaResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        // Primeiro confere o dono: a categoria de outro usuário responde 404, nunca o 409 abaixo
        // (o 409 revelaria que a categoria existe e tem transações).
        Categoria categoria = buscarDoUsuario(id, usuarioId);
        if (transacaoRepository.existsByCategoriaId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A categoria possui transações e não pode ser excluída. Arquive a categoria em vez de excluí-la.");
        }
        categoriaRepository.delete(categoria);
    }

    /**
     * Busca a categoria entre as do usuário. Também é usado pelo TransacaoService, para garantir
     * que a categoria de uma transação é do mesmo dono.
     */
    @Transactional(readOnly = true)
    public Categoria buscarDoUsuario(Long id, Long usuarioId) {
        return categoriaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada com o ID: " + id));
    }
}
