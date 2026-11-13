package com.hotelai.reporting.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException error, HttpServletRequest request) {
        return problem(error.status(), error.code(), error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException error, HttpServletRequest request) {
        String detail = error.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(value -> value.getField() + ": " + value.getDefaultMessage())
                .orElse("Request validation failed");
        return problem(400, "validation-error", detail, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
        log.error(
                "Unhandled request failure for {} {}",
                request.getMethod(),
                request.getRequestURI(),
                error);
        return problem(500, "internal-error", "The request could not be completed", request);
    }

    private static ResponseEntity<ProblemDetail> problem(
            int status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(status), detail);
        problem.setTitle(code);
        problem.setType(java.net.URI.create("https://hotel-ai.local/problems/" + code));
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperties(Map.of(
                "code", code,
                "correlationId", String.valueOf(MDC.get("correlationId"))));
        return ResponseEntity.status(status).body(problem);
    }
}
