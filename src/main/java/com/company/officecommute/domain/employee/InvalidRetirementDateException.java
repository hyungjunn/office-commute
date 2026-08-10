package com.company.officecommute.domain.employee;

import java.time.LocalDate;

public class InvalidRetirementDateException extends RuntimeException {
    public InvalidRetirementDateException(LocalDate workEndDate, LocalDate workStartDate) {
        super(String.format("퇴사일(%s)은 입사일(%s)보다 이전일 수 없습니다.", workEndDate, workStartDate));
    }
}
