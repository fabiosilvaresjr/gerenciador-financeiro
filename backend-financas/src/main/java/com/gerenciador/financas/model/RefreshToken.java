package com.gerenciador.financas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Um refresh token emitido para um usuário. Só o hash (SHA-256) do token fica gravado.
 *
 * Ciclo de vida: nasce no login (família nova) ou num refresh (mesma família do token trocado).
 * Pode ser usado uma vez só: ao ser trocado, ganha "usadoEm". No logout, ou se um token já usado
 * reaparecer, todos os tokens da família ganham "revogadoEm".
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dono do token: o usuário vem sempre desta linha, nunca da requisição.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familia;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;
}
