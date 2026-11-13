package com.hotelai.pricing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
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

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  ResponseEntity<ProblemDetail> validation(Exception exception, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (exception instanceof MethodArgumentNotValidException invalid) {
      invalid
          .getBindingResult()
          .getFieldErrors()
          .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    }
    return response(
        HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", request, fields);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ProblemDetail> missingHeader(
      MissingRequestHeaderException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "missing_required_header",
        exception.getHeaderName() + " is required",
        request,
        null);
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    DataIntegrityViolationException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception exception, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "concurrent_update",
        "The resource was changed by another request; reload and retry",
        request,
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(
      AccessDeniedException exception, HttpServletRequest request) {
    return response(
        HttpStatus.FORBIDDEN,
        "access_denied",
        "You are not allowed to perform this operation",
        request,
        null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "unexpected_error",
        "An unexpected error occurred",
        request,
        null);
  }

  private static ResponseEntity<ProblemDetail> response(
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
    if (fields != null && !fields.isEmpty()) {
      problem.setProperty("fields", fields);
    }
    return ResponseEntity.status(status).body(problem);
  }
}
