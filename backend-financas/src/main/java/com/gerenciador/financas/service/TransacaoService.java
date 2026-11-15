package com.gerenciador.financas.service;

import com.gerenciador.financas.dto.TransacaoRequestDTO;
import com.gerenciador.financas.dto.TransacaoResponseDTO;
import com.gerenciador.financas.model.Categoria;
import com.gerenciador.financas.model.Transacao;
import com.gerenciador.financas.repository.CategoriaRepository;
import com.gerenciador.financas.repository.TransacaoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<TransacaoResponseDTO> listar() {
        return transacaoRepository.findAll().stream()
                .map(TransacaoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransacaoResponseDTO buscarPorId(Long id) {
        Transacao transacao = transacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada com o ID: " + id));
        return TransacaoResponseDTO.fromEntity(transacao);
    }

    @Transactional
    public TransacaoResponseDTO criar(TransacaoRequestDTO dto) {
        Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada com o ID: " + dto.getCategoriaId()));

        Transacao transacao = Transacao.builder()
                .descricao(dto.getDescricao())
                .valor(dto.getValor())
                .data(dto.getData())
                .tipo(dto.getTipo())
                .categoria(categoria)
                .build();

        Transacao salva = transacaoRepository.save(transacao);
        return TransacaoResponseDTO.fromEntity(salva);
    }

    @Transactional
    public TransacaoResponseDTO atualizar(Long id, TransacaoRequestDTO dto) {
        Transacao transacao = transacaoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada com o ID: " + id));

        Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada com o ID: " + dto.getCategoriaId()));

        transacao.setDescricao(dto.getDescricao());
        transacao.setValor(dto.getValor());
        transacao.setData(dto.getData());
        transacao.setTipo(dto.getTipo());
        transacao.setCategoria(categoria);

        Transacao atualizada = transacaoRepository.save(transacao);
        return TransacaoResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void deletar(Long id) {
        if (!transacaoRepository.existsById(id)) {
            throw new EntityNotFoundException("Transação não encontrada com o ID: " + id);
        }
        transacaoRepository.deleteById(id);
    }
}
