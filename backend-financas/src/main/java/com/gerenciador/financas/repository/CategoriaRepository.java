package com.gerenciador.financas.repository;

import com.gerenciador.financas.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    // Consultas derivadas do nome. "UsuarioId" navega até usuario.id (WHERE usuario_id = ?).
    // Todas as categorias do usuário, inclusive as arquivadas.
    List<Categoria> findByUsuarioId(Long usuarioId);

    // Só as ativas: WHERE usuario_id = ? AND arquivada = false.
    List<Categoria> findByUsuarioIdAndArquivadaFalse(Long usuarioId);

    // Busca pelo ID só entre as categorias do usuário: a de outro usuário volta vazia, como se não existisse.
    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);

    // A "Sem categoria" do usuário: WHERE usuario_id = ? AND sem_categoria = true.
    Optional<Categoria> findByUsuarioIdAndSemCategoriaTrue(Long usuarioId);
}
