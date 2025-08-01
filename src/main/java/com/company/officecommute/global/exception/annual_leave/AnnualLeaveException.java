package com.company.officecommute.global.exception.annual_leave;

import com.company.officecommute.global.exception.ErrorCode;

public class AnnualLeaveException extends RuntimeException {

    private final ErrorCode errorCode;

    public AnnualLeaveException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
