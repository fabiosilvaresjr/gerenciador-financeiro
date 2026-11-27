package com.gerenciador.financas.controller;

import com.gerenciador.financas.dto.CategoriaRequestDTO;
import com.gerenciador.financas.dto.CategoriaResponseDTO;
import com.gerenciador.financas.service.CategoriaService;
import jakarta.validation.Valid;
import com.gerenciador.financas.security.services.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @AuthenticationPrincipal entrega o usuário que o AuthTokenFilter colocou no contexto de segurança
// a partir do token. O service usa o ID dele para trabalhar só com os dados desse usuário.
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar(@AuthenticationPrincipal UserDetailsImpl usuario) {
        return ResponseEntity.ok(categoriaService.listar(usuario.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> buscarPorId(@PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        return ResponseEntity.ok(categoriaService.buscarPorId(id, usuario.getId()));
    }

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> criar(@RequestBody @Valid CategoriaRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        CategoriaResponseDTO novaCategoria = categoriaService.criar(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(novaCategoria);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid CategoriaRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        CategoriaResponseDTO categoriaAtualizada = categoriaService.atualizar(id, dto, usuario.getId());
        return ResponseEntity.ok(categoriaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        categoriaService.deletar(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
