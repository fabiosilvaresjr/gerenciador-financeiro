package com.gerenciador.financas.controller;

import com.gerenciador.financas.dto.TransacaoRequestDTO;
import com.gerenciador.financas.dto.TransacaoResponseDTO;
import com.gerenciador.financas.service.TransacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transacoes")
@RequiredArgsConstructor
public class TransacaoController {

    private final TransacaoService transacaoService;

    @GetMapping
    public ResponseEntity<List<TransacaoResponseDTO>> listar() {
        return ResponseEntity.ok(transacaoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransacaoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(transacaoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<TransacaoResponseDTO> criar(@RequestBody @Valid TransacaoRequestDTO dto) {
        TransacaoResponseDTO novaTransacao = transacaoService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaTransacao);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransacaoResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid TransacaoRequestDTO dto) {
        TransacaoResponseDTO transacaoAtualizada = transacaoService.atualizar(id, dto);
        return ResponseEntity.ok(transacaoAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        transacaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
