package com.hotelai.reservation.common;

import com.hotelai.reservation.config.CorrelationIdFilter;
import com.hotelai.reservation.integration.IntegrationException;
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

    @ExceptionHandler(IntegrationException.class)
    ProblemDetail integration(IntegrationException error, HttpServletRequest request) {
        int status = error.upstreamStatus() == 409 || error.upstreamStatus() == 422 ? 409 : 503;
        return problem(status, "dependency-unavailable", error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException error, HttpServletRequest request) {
        ProblemDetail problem = problem(400, "validation", "Request validation failed", request);
        Map<String, String> violations = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field ->
                violations.putIfAbsent(field.getField(), field.getDefaultMessage()));
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidConstraint(ConstraintViolationException error, HttpServletRequest request) {
        return problem(400, "validation", "Request validation failed", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail optimistic(ObjectOptimisticLockingFailureException error, HttpServletRequest request) {
        return problem(409, "optimistic-lock", "Reservation was changed by another request", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(DataIntegrityViolationException error, HttpServletRequest request) {
        return problem(409, "data-conflict", "The request conflicts with an existing reservation operation", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException error, HttpServletRequest request) {
        return problem(400, "validation", error.getMessage(), request);
    }

    private static ProblemDetail problem(
            int status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setType(URI.create("https://hotel-ai.local/problems/" + code));
        problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
