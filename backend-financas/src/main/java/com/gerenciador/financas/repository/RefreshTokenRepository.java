package com.gerenciador.financas.repository;

import com.gerenciador.financas.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marca o token como usado só se ele ainda não foi usado, e devolve quantas linhas mudaram.
     * Se duas requisições chegarem ao mesmo tempo com o mesmo token, o banco deixa só uma
     * mudar a linha (1); a outra recebe 0 e é tratada como reuso.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.usadoEm = :agora WHERE r.id = :id AND r.usadoEm IS NULL")
    int marcarComoUsado(@Param("id") Long id, @Param("agora") Instant agora);

    /** Revoga todos os tokens ainda não revogados de uma família (encerra a sessão). */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogadoEm = :agora WHERE r.familia = :familia AND r.revogadoEm IS NULL")
    int revogarFamilia(@Param("familia") UUID familia, @Param("agora") Instant agora);
}
