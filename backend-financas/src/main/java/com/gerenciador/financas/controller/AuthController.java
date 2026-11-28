package com.gerenciador.financas.controller;

import com.gerenciador.financas.dto.auth.JwtResponseDTO;
import com.gerenciador.financas.dto.auth.LoginRequestDTO;
import com.gerenciador.financas.dto.auth.RefreshTokenRequestDTO;
import com.gerenciador.financas.dto.auth.RegisterRequestDTO;
import com.gerenciador.financas.dto.auth.UsuarioResponseDTO;
import com.gerenciador.financas.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UsuarioResponseDTO> registrar(@RequestBody @Valid RegisterRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponseDTO> refresh(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        return ResponseEntity.ok(authService.refresh(dto));
    }

    // Sempre 204, mesmo para um token desconhecido ou já revogado: a resposta não revela nada.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        authService.logout(dto);
        return ResponseEntity.noContent().build();
    }
}
