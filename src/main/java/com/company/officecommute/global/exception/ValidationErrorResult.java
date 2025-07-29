package com.company.officecommute.global.exception;

import java.util.List;

public record ValidationErrorResult(
        String code,
        String message,
        List<FieldErrorResult> fieldErrorResults
) {
}

record FieldErrorResult(
        String field,
        String message
) {
}
