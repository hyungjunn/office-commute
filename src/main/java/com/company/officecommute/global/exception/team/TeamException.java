package com.company.officecommute.global.exception.team;

import com.company.officecommute.global.exception.ErrorCode;

public class TeamException extends RuntimeException {

    private final ErrorCode errorCode;

    public TeamException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
