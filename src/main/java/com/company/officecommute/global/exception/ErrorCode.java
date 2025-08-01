package com.company.officecommute.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 직원 관련
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "EMP_001", "해당하는 직원이 존재하지 않습니다."),
    EMPLOYEE_ALREADY_WORK(HttpStatus.BAD_REQUEST, "EMP_002", "이미 출근한 상태입니다."),

    // 팀 관련
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_001", "해당하는 팀이 없습니다."),
    DUPLICATE_TEAM_NAME(HttpStatus.BAD_REQUEST, "TEAM_002", "이미 존재하는 팀명입니다."),

    // 연차 관련
    INVALID_ANNUAL_LEAVE_DATE(HttpStatus.NOT_FOUND, "ANN_001", "과거 날짜로 연차 신청할 수 없습니다."),
    DUPLICATE_ANNUAL_LEAVE(HttpStatus.BAD_REQUEST, "ANN_002", "이미 등록된 연차입니다."),

    // 기본 오류 코드
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
