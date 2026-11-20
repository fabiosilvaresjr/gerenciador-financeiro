package com.gerenciador.financas.service;

import com.gerenciador.financas.config.JwtUtils;
import com.gerenciador.financas.dto.auth.JwtResponseDTO;
import com.gerenciador.financas.dto.auth.LoginRequestDTO;
import com.gerenciador.financas.dto.auth.RegisterRequestDTO;
import com.gerenciador.financas.dto.auth.UsuarioResponseDTO;
import com.gerenciador.financas.model.ERole;
import com.gerenciador.financas.model.Role;
import com.gerenciador.financas.model.Usuario;
import com.gerenciador.financas.repository.RoleRepository;
import com.gerenciador.financas.repository.UsuarioRepository;
import com.gerenciador.financas.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @Transactional
    public UsuarioResponseDTO registrar(RegisterRequestDTO dto) {
        if (usuarioRepository.existsByUsername(dto.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username já está em uso");
        }
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email já está em uso");
        }

        Role roleUser = roleRepository.findByNome(ERole.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("Papel ROLE_USER não encontrado no banco"));

        Usuario usuario = Usuario.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .roles(Set.of(roleUser))
                .build();

        return UsuarioResponseDTO.fromEntity(usuarioRepository.save(usuario));
    }

    public JwtResponseDTO login(LoginRequestDTO dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String token = jwtUtils.generateJwtToken(userDetails.getUsername());
        return JwtResponseDTO.of(token, userDetails);
    }
}
