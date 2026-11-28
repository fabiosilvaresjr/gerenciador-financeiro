package com.gerenciador.financas.service;

import com.gerenciador.financas.dto.TransacaoRequestDTO;
import com.gerenciador.financas.dto.TransacaoResponseDTO;
import com.gerenciador.financas.model.Categoria;
import com.gerenciador.financas.model.Transacao;
import com.gerenciador.financas.repository.TransacaoRepository;
import com.gerenciador.financas.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Todos os métodos recebem o ID do usuário logado (vindo do token) e só trabalham com as
 * transações dele. A transação de outro usuário é tratada como inexistente (404), e a categoria
 * informada também precisa ser do usuário.
 */
@Service
@RequiredArgsConstructor
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final CategoriaService categoriaService;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<TransacaoResponseDTO> listar(Long usuarioId) {
        return transacaoRepository.findByUsuarioId(usuarioId).stream()
                .map(TransacaoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransacaoResponseDTO buscarPorId(Long id, Long usuarioId) {
        return TransacaoResponseDTO.fromEntity(buscarDoUsuario(id, usuarioId));
    }

    @Transactional
    public TransacaoResponseDTO criar(TransacaoRequestDTO dto, Long usuarioId) {
        // Sem categoria → "Sem categoria"; de outro usuário → 404; arquivada → 409.
        Categoria categoria = categoriaService.buscarParaTransacao(dto.getCategoriaId(), usuarioId);

        Transacao transacao = Transacao.builder()
                .descricao(dto.getDescricao())
                .valor(dto.getValor())
                .data(dto.getData())
                .tipo(dto.getTipo())
                .categoria(categoria)
                .usuario(usuarioRepository.getReferenceById(usuarioId))
                .build();

        Transacao salva = transacaoRepository.save(transacao);
        return TransacaoResponseDTO.fromEntity(salva);
    }

    @Transactional
    public TransacaoResponseDTO atualizar(Long id, TransacaoRequestDTO dto, Long usuarioId) {
        Transacao transacao = buscarDoUsuario(id, usuarioId);
        Categoria categoria = categoriaDaAtualizacao(transacao, dto.getCategoriaId(), usuarioId);

        transacao.setDescricao(dto.getDescricao());
        transacao.setValor(dto.getValor());
        transacao.setData(dto.getData());
        transacao.setTipo(dto.getTipo());
        transacao.setCategoria(categoria);

        Transacao atualizada = transacaoRepository.save(transacao);
        return TransacaoResponseDTO.fromEntity(atualizada);
    }

    @Transactional
    public void deletar(Long id, Long usuarioId) {
        transacaoRepository.delete(buscarDoUsuario(id, usuarioId));
    }

    /**
     * Uma transação antiga pode continuar na categoria dela mesmo que ela tenha sido arquivada
     * (senão não daria para corrigir o valor ou a descrição). A regra do arquivamento só vale
     * para a categoria nova, quando a transação muda de categoria.
     */
    private Categoria categoriaDaAtualizacao(Transacao transacao, Long categoriaId, Long usuarioId) {
        Categoria atual = transacao.getCategoria();
        if (categoriaId != null && categoriaId.equals(atual.getId())) {
            return atual;
        }
        return categoriaService.buscarParaTransacao(categoriaId, usuarioId);
    }

    private Transacao buscarDoUsuario(Long id, Long usuarioId) {
        return transacaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada com o ID: " + id));
    }
}
