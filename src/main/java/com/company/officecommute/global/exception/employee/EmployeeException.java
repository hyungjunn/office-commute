package com.company.officecommute.global.exception.employee;

import com.company.officecommute.global.exception.ErrorCode;

public class EmployeeException extends RuntimeException{

    private final ErrorCode errorCode;

    public EmployeeException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
