package com.gerenciador.financas.repository;

import com.gerenciador.financas.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Consultas derivadas do nome. "UsuarioId" navega até usuario.id (WHERE usuario_id = ?).
    List<Categoria> findByUsuarioId(Long usuarioId);

    // Busca pelo ID só entre as categorias do usuário: a de outro usuário volta vazia, como se não existisse.
    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);
}
