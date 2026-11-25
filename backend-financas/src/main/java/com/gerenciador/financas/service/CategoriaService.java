package com.gerenciador.financas.service;

import com.gerenciador.financas.dto.CategoriaRequestDTO;
import com.gerenciador.financas.dto.CategoriaResponseDTO;
import com.gerenciador.financas.model.Categoria;
import com.gerenciador.financas.repository.CategoriaRepository;
import com.gerenciador.financas.repository.TransacaoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listar() {
        return categoriaRepository.findAll().stream()
                .map(CategoriaResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoriaResponseDTO buscarPorId(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada com o ID: " + id));
        return CategoriaResponseDTO.fromEntity(categoria);
    }

    @Transactional
    public CategoriaResponseDTO criar(CategoriaRequestDTO dto) {
        Categoria categoria = Categoria.builder()
                .nome(dto.getNome())
                .build();
        Categoria salva = categoriaRepository.save(categoria);
        return CategoriaResponseDTO.fromEntity(salva);
    }

    @Transactional
    public CategoriaResponseDTO atualizar(Long id, CategoriaRequestDTO dto) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada com o ID: " + id));
        categoria.setNome(dto.getNome());
        Categoria atualizada = categoriaRepository.save(categoria);
        return CategoriaResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void deletar(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new EntityNotFoundException("Categoria não encontrada com o ID: " + id);
        }
        if (transacaoRepository.existsByCategoriaId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A categoria possui transações e não pode ser excluída. Arquive a categoria em vez de excluí-la.");
        }
        categoriaRepository.deleteById(id);
    }
}
