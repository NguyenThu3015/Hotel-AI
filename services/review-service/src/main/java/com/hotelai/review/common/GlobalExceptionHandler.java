package com.hotelai.review.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException error, HttpServletRequest request) {
        return problem(error.status(), error.code(), error.getMessage(), request, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> badRequest(Exception error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", error.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(
            MethodArgumentNotValidException error, HttpServletRequest request) {
        Map<String, String> fields = error.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        value -> value.getDefaultMessage() == null
                                ? "Invalid value" : value.getDefaultMessage(),
                        (first, ignored) -> first));
        return problem(
                HttpStatus.BAD_REQUEST, "validation",
                "Request validation failed", request, fields);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, String> fields) {
        ProblemDetail value = ProblemDetail.forStatusAndDetail(status, detail);
        value.setTitle(code);
        value.setType(URI.create("https://hotel-ai.local/problems/" + code));
        value.setInstance(URI.create(request.getRequestURI()));
        if (fields != null) value.setProperty("fields", fields);
        return ResponseEntity.status(status).body(value);
    }
}
