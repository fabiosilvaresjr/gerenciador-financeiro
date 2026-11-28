package com.gerenciador.financas.dto.auth;

import com.gerenciador.financas.security.services.UserDetailsImpl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtResponseDTO {

    private String token;
    @Builder.Default
    private String tipo = "Bearer";
    // Serve só para o POST /api/auth/refresh. Vale uma vez: cada refresh devolve um novo.
    private String refreshToken;
    private Long id;
    private String username;
    private String email;
    private List<String> roles;

    public static JwtResponseDTO of(String token, String refreshToken, UserDetailsImpl userDetails) {
        return JwtResponseDTO.builder()
                .token(token)
                .refreshToken(refreshToken)
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .email(userDetails.getEmail())
                .roles(userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList())
                .build();
    }
}
