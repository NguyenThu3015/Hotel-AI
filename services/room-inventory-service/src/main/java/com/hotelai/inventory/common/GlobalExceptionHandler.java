package com.hotelai.inventory.common;

import com.hotelai.inventory.config.CorrelationIdFilter;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex, HttpServletRequest request) {
        return problem(ex.status(), ex.problemCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail optimistic(Exception ex, HttpServletRequest request) {
        return problem(409, "stale-version", "Dữ liệu vừa được cập nhật bởi yêu cầu khác; hãy tải lại.", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ProblemDetail validation(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = problem(400, "validation", "Một hoặc nhiều trường không hợp lệ.", request);
        if (ex instanceof MethodArgumentNotValidException invalid) {
            Map<String, String> errors = new LinkedHashMap<>();
            invalid.getBindingResult().getFieldErrors().forEach(error ->
                    errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return problem(400, "missing-header", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return problem(409, "data-conflict", "Mã hoặc giá trị duy nhất đã được sử dụng.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        return problem(500, "internal", "Không thể xử lý yêu cầu vào lúc này.", request);
    }

    private static ProblemDetail problem(
            int status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setTitle(title(status));
        problem.setType(URI.create("https://hotel-ai.local/problems/inventory/" + code));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return problem;
    }

    private static String title(int status) {
        return switch (status) {
            case 400 -> "Dữ liệu không hợp lệ";
            case 401 -> "Chưa xác thực";
            case 403 -> "Không có quyền";
            case 404 -> "Không tìm thấy";
            case 409 -> "Xung đột dữ liệu";
            default -> "Lỗi hệ thống";
        };
    }
}
