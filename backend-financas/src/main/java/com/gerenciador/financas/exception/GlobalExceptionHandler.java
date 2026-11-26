package com.gerenciador.financas.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tratamento de erros único para todos os controllers.
 *
 * Toda resposta de erro tem o mesmo corpo JSON do AuthEntryPointJwt:
 * { "timestamp", "status", "error", "message", "path" }
 * (o 400 de validação tem, além disso, a lista "campos").
 *
 * Observação: o 401 de token ausente/inválido acontece no filtro de segurança, antes de
 * chegar a um controller, por isso continua sendo respondido pelo AuthEntryPointJwt.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 404: recurso não existe (lançado pelos services com findById(...).orElseThrow)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(EntityNotFoundException e, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    // 400: falha do @Valid no corpo da requisição; devolve um item por campo inválido
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> dadosInvalidos(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<Map<String, String>> campos = e.getBindingResult().getFieldErrors().stream()
                .map(this::campoInvalido)
                .toList();

        ResponseEntity<Map<String, Object>> resposta = resposta(HttpStatus.BAD_REQUEST, "Dados inválidos", request);
        resposta.getBody().put("campos", campos);
        return resposta;
    }

    // 401: username ou senha errados no login (o AuthenticationManager lança AuthenticationException)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> credenciaisInvalidas(AuthenticationException e, HttpServletRequest request) {
        log.warn("Falha de autenticação em {}: {}", request.getServletPath(), e.getMessage());
        return resposta(HttpStatus.UNAUTHORIZED, "Username ou senha inválidos", request);
    }

    // Status escolhido pelo próprio service (ex.: 409 para username/email em uso
    // ou para categoria com transações). Usa o status e a mensagem da exceção.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> erroDeStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return resposta(status, e.getReason(), request);
    }

    private Map<String, String> campoInvalido(FieldError erro) {
        Map<String, String> campo = new LinkedHashMap<>();
        campo.put("campo", erro.getField());
        campo.put("mensagem", erro.getDefaultMessage());
        return campo;
    }

    // Monta o corpo padrão de erro
    private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String mensagem, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", mensagem);
        body.put("path", request.getServletPath());
        return ResponseEntity.status(status).body(body);
    }
}
