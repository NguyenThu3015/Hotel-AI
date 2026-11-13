package com.hotelai.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  HttpStatus status() {
    return status;
  }

  String code() {
    return code;
  }
}

@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException exception, HttpServletRequest request) {
    return response(exception.status(), exception.code(), exception.getMessage(), request, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(
        HttpStatus.BAD_REQUEST,
        "validation_failed",
        "Dữ liệu yêu cầu không hợp lệ",
        request,
        fields);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "data_conflict",
        "Dữ liệu bị trùng hoặc đã thay đổi đồng thời",
        request,
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(
      AccessDeniedException exception, HttpServletRequest request) {
    return response(
        HttpStatus.FORBIDDEN,
        "access_denied",
        "Không có quyền thực hiện thao tác này",
        request,
        null);
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status,
      String code,
      String detail,
      HttpServletRequest request,
      Map<String, String> fields) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setType(URI.create("https://hotel-ai.local/problems/" + code));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("correlationId", CorrelationContext.current());
    if (fields != null && !fields.isEmpty()) problem.setProperty("fields", fields);
    return ResponseEntity.status(status).body(problem);
  }
}
