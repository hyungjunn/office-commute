package com.company.officecommute.global.exception;

import com.company.officecommute.auth.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResult illegalExHandler(IllegalArgumentException e) {
        log.error("IllegalArgumentException", e);
        return new ErrorResult("BAD_REQUEST", e.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResult exHandler(Exception e) {
        log.error("Exception", e);
        return new ErrorResult("INTERNAL_SERVER_ERROR", "내부 서버 오류가 발생했습니다");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ValidationErrorResult validationExHandler(MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException", e);
        List<FieldErrorResult> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResult(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ValidationErrorResult("VALIDATION_ERROR", "입력값이 올바르지 않습니다", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResult handleInvalidJson(HttpMessageNotReadableException e) {
        log.warn("JSON 파싱 실패", e);
        return new ErrorResult("INVALID_JSON", "역할 값이 올바르지 않습니다.");
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public ErrorResult forbiddenExceptionHandler(ForbiddenException e) {
        log.error("ForbiddenException", e);
        return new ErrorResult("FORBIDDEN", e.getMessage());
    }
}
