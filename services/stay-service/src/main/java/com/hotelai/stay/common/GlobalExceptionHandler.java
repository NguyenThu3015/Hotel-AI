package com.hotelai.stay.common;

import com.hotelai.stay.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException error, HttpServletRequest request) {
        return problem(error.status(), error.code(), error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST, "validation", "Request validation failed", request);
        Map<String, String> violations = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field ->
                violations.putIfAbsent(field.getField(), field.getDefaultMessage()));
        detail.setProperty("violations", violations);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "Request validation failed", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail optimistic(ObjectOptimisticLockingFailureException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "optimistic-lock", "Stay data changed concurrently", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "data-conflict", "Command conflicts with existing stay data", request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ProblemDetail domain(RuntimeException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "invalid-state", error.getMessage(), request);
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setType(URI.create("https://hotel-ai.local/problems/" + code));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
