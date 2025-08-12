package com.company.officecommute.global.exception;

import com.company.officecommute.auth.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResult handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Business logic error: {}", e.getMessage());
        return new ErrorResult("BAD_REQUEST", e.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResult handleSystemError(Exception e) {
        log.error("Unexpected system error", e);
        return new ErrorResult("INTERNAL_SERVER_ERROR", "내부 서버 오류가 발생했습니다");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ValidationErrorResult handleValidation(MethodArgumentNotValidException e) {
        log.warn("Validation failed for request", e);
        List<FieldErrorResult> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResult(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ValidationErrorResult("VALIDATION_ERROR", "입력값이 올바르지 않습니다", errors);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResult handleInvalidJson(HttpMessageNotReadableException e) {
        log.warn("Invalid JSON request: {}", e.getMessage());
        return new ErrorResult("INVALID_JSON", "역할 값이 올바르지 않습니다.");
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public ErrorResult handleForbidden(ForbiddenException e) {
        log.warn("Access denied: {}", e.getMessage());
        return new ErrorResult("FORBIDDEN", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ErrorResult handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data constraint violation: {}", e.getMessage());

        // Unique Constraint 위반 메시지 확인
        String message = Objects.requireNonNull(e.getRootCause()).getMessage().toLowerCase();
        if (message.contains("employee_id") && message.contains("work_date")) {
            return new ErrorResult("DUPLICATE_WORK", "이미 오늘 출근 등록을 했습니다");
        }

        return new ErrorResult("DATA_INTEGRITY_ERROR", "데이터 제약조건을 위반했습니다");
    }
}
