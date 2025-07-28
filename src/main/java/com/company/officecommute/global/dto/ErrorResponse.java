package com.company.officecommute.global.dto;

import com.company.officecommute.global.exception.ErrorCode;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        int status,
        String code,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getMessage(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                LocalDateTime.now()
        );
    }
}
