package com.example.foodrescue.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final String TYPE_PREFIX = "https://api.foodrescue.local/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Дані неправильні");
        problem.setType(URI.create(TYPE_PREFIX + "validation"));
        problem.setTitle("Помилка валідації");
        problem.setInstance(URI.create(request.getRequestURI()));

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create(TYPE_PREFIX + typeSuffix(status)));
        problem.setTitle(title(status));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private String typeSuffix(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "not-found";
            case CONFLICT -> "conflict";
            case FORBIDDEN -> "forbidden";
            case UNPROCESSABLE_ENTITY -> "business-rule";
            default -> "business-rule";
        };
    }

    private String title(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Не знайдено";
            case CONFLICT -> "Конфлікт стану";
            case FORBIDDEN -> "Дію заборонено";
            case UNPROCESSABLE_ENTITY -> "Порушено бізнес-правило";
            default -> "Порушено бізнес-правило";
        };
    }
}
