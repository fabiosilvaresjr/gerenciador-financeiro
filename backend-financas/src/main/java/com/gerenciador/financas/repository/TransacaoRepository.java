package com.gerenciador.financas.repository;

import com.gerenciador.financas.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    // Consulta derivada do nome: "existe alguma transação com categoria.id = ?"
    boolean existsByCategoriaId(Long categoriaId);

    List<Transacao> findByUsuarioId(Long usuarioId);

    // Busca pelo ID só entre as transações do usuário: a de outro usuário volta vazia, como se não existisse.
    Optional<Transacao> findByIdAndUsuarioId(Long id, Long usuarioId);
}
