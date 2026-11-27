package com.gerenciador.financas.controller;

import com.gerenciador.financas.dto.TransacaoRequestDTO;
import com.gerenciador.financas.dto.TransacaoResponseDTO;
import com.gerenciador.financas.service.TransacaoService;
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
@RequestMapping("/api/transacoes")
@RequiredArgsConstructor
public class TransacaoController {

    private final TransacaoService transacaoService;

    @GetMapping
    public ResponseEntity<List<TransacaoResponseDTO>> listar(@AuthenticationPrincipal UserDetailsImpl usuario) {
        return ResponseEntity.ok(transacaoService.listar(usuario.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransacaoResponseDTO> buscarPorId(@PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        return ResponseEntity.ok(transacaoService.buscarPorId(id, usuario.getId()));
    }

    @PostMapping
    public ResponseEntity<TransacaoResponseDTO> criar(@RequestBody @Valid TransacaoRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        TransacaoResponseDTO novaTransacao = transacaoService.criar(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(novaTransacao);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransacaoResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid TransacaoRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        TransacaoResponseDTO transacaoAtualizada = transacaoService.atualizar(id, dto, usuario.getId());
        return ResponseEntity.ok(transacaoAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl usuario) {
        transacaoService.deletar(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
