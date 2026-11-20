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
    private Long id;
    private String username;
    private String email;
    private List<String> roles;

    public static JwtResponseDTO of(String token, UserDetailsImpl userDetails) {
        return JwtResponseDTO.builder()
                .token(token)
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .email(userDetails.getEmail())
                .roles(userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList())
                .build();
    }
}
