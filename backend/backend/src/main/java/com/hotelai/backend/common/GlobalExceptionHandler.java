package com.hotelai.backend.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException ex) {
    return error(ex.status(), ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", fields);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException ex) {
    return error(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc đang được tham chiếu", null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied(AccessDeniedException ex) {
    return error(HttpStatus.FORBIDDEN, "Không có quyền thực hiện", null);
  }

  private ResponseEntity<?> error(HttpStatus status, String message, Object details) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "timestamp",
                Instant.now(),
                "status",
                status.value(),
                "error",
                status.getReasonPhrase(),
                "message",
                message,
                "details",
                details == null ? Map.of() : details));
  }
}
