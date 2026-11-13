package com.hotelai.customer.common;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;
import com.hotelai.customer.config.CorrelationIdFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex) {
        return problem(ex.status(), ex.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict(Exception ex) {
        return problem(409, "The customer record was changed by another request");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail validation(Exception ex) {
        return problem(400, "Request validation failed");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail duplicate(DataIntegrityViolationException ex) {
        return problem(409, "A unique customer value is already in use");
    }

    private static ProblemDetail problem(int status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setType(URI.create("urn:hotel-ai:customer:error:" + status));
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }
}
