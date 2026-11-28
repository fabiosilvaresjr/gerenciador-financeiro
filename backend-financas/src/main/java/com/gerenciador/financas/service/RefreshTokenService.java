package com.gerenciador.financas.service;

import com.gerenciador.financas.model.RefreshToken;
import com.gerenciador.financas.repository.RefreshTokenRepository;
import com.gerenciador.financas.repository.UsuarioRepository;
import com.gerenciador.financas.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cria, troca (rotação) e revoga refresh tokens.
 *
 * - O token é um valor aleatório "opaco" (não é um JWT): só tem significado para o nosso banco.
 * - No banco fica só o SHA-256 dele. O valor puro só existe na resposta HTTP.
 * - Cada token vale uma vez. Se um token já usado reaparecer, a família inteira é revogada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /** Mesma mensagem para token desconhecido, vencido, já usado ou revogado: a resposta não revela qual foi. */
    static final String MENSAGEM_INVALIDO = "Refresh token inválido ou expirado";

    private static final int TAMANHO_EM_BYTES = 32; // 256 bits aleatórios
    private static final SecureRandom GERADOR = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${financas.app.refreshExpirationMs}")
    private long refreshExpirationMs;

    /** O que o refresh devolve: o dono do token trocado e o refresh token novo (valor puro). */
    public record TokenRotacionado(UserDetailsImpl usuario, String refreshToken) {
    }

    /** Login: abre uma sessão nova (família nova) e devolve o refresh token puro. */
    @Transactional
    public String criarSessao(Long usuarioId) {
        RefreshToken refreshToken = novoRefreshToken(UUID.randomUUID());
        refreshToken.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        return salvar(refreshToken);
    }

    /**
     * Troca um refresh token válido por um novo, na mesma família.
     *
     * noRollbackFor: no reuso, a família é revogada e em seguida sai o 401 (uma exceção). Sem isso,
     * o Spring desfaria a transação por causa da exceção, e a revogação seria perdida.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenRotacionado rotacionar(String tokenPuro) {
        RefreshToken atual = refreshTokenRepository.findByTokenHash(hash(tokenPuro))
                .orElseThrow(this::invalido);
        Instant agora = Instant.now();

        if (atual.getRevogadoEm() != null) {
            throw invalido();
        }
        // Já usado: alguém tem uma cópia (o ladrão ou o usuário). Não dá para saber quem é quem,
        // então a sessão acaba para os dois. Também cobre duas requisições simultâneas com o mesmo token.
        if (atual.getUsadoEm() != null || refreshTokenRepository.marcarComoUsado(atual.getId(), agora) == 0) {
            log.warn("Reuso de refresh token detectado: família {} do usuário {} revogada",
                    atual.getFamilia(), atual.getUsuario().getId());
            refreshTokenRepository.revogarFamilia(atual.getFamilia(), agora);
            throw invalido();
        }
        if (!atual.getExpiraEm().isAfter(agora)) {
            throw invalido();
        }

        RefreshToken novo = novoRefreshToken(atual.getFamilia());
        novo.setUsuario(atual.getUsuario());
        String novoTokenPuro = salvar(novo);

        // Montado aqui dentro, com a transação aberta, porque os papéis do usuário são LAZY.
        return new TokenRotacionado(UserDetailsImpl.build(atual.getUsuario()), novoTokenPuro);
    }

    /** Logout: revoga a família do token. Token desconhecido ou já revogado não é erro (idempotente). */
    @Transactional
    public void revogarSessao(String tokenPuro) {
        refreshTokenRepository.findByTokenHash(hash(tokenPuro))
                .ifPresent(token -> refreshTokenRepository.revogarFamilia(token.getFamilia(), Instant.now()));
    }

    /**
     * SHA-256 em hexadecimal. Um hash rápido basta porque o token tem 256 bits aleatórios (não dá
     * para adivinhar, ao contrário de uma senha). E, sem "sal", o mesmo token sempre dá o mesmo hash,
     * o que permite procurá-lo no banco com WHERE token_hash = ?.
     */
    static String hash(String tokenPuro) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(tokenPuro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e); // todo Java tem SHA-256
        }
    }

    // ---------- auxiliares ----------

    private RefreshToken novoRefreshToken(UUID familia) {
        Instant agora = Instant.now();
        return RefreshToken.builder()
                .familia(familia)
                .criadoEm(agora)
                .expiraEm(agora.plusMillis(refreshExpirationMs))
                .build();
    }

    /** Gera o valor aleatório, grava só o hash dele e devolve o valor puro. */
    private String salvar(RefreshToken refreshToken) {
        byte[] bytes = new byte[TAMANHO_EM_BYTES];
        GERADOR.nextBytes(bytes);
        String tokenPuro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshToken.setTokenHash(hash(tokenPuro));
        refreshTokenRepository.save(refreshToken);
        return tokenPuro;
    }

    private ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAGEM_INVALIDO);
    }
}
