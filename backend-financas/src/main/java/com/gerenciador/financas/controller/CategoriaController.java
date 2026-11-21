package com.gerenciador.financas.controller;

import com.gerenciador.financas.dto.CategoriaRequestDTO;
import com.gerenciador.financas.dto.CategoriaResponseDTO;
import com.gerenciador.financas.service.CategoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar() {
        return ResponseEntity.ok(categoriaService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(categoriaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> criar(@RequestBody @Valid CategoriaRequestDTO dto) {
        CategoriaResponseDTO novaCategoria = categoriaService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaCategoria);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid CategoriaRequestDTO dto) {
        CategoriaResponseDTO categoriaAtualizada = categoriaService.atualizar(id, dto);
        return ResponseEntity.ok(categoriaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    // Sem este handler, o Spring devolveria o 409 sem a mensagem (server.error.include-message=never).
    // Provisório: o tratamento global de erros (@RestControllerAdvice) vai absorver isto.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> erroDeStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", e.getReason());
        body.put("path", request.getServletPath());
        return ResponseEntity.status(status).body(body);
    }
}
