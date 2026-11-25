package com.gerenciador.financas.repository;

import com.gerenciador.financas.model.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    // Consulta derivada do nome: "existe alguma transação com categoria.id = ?"
    boolean existsByCategoriaId(Long categoriaId);
}
