package com.company.officecommute.service.overtime;

import java.time.LocalDate;

/**
 * 기간 조회용 직원별·일별 근무 분 프로젝션. (employee_id, work_date) unique 제약으로 하루 한 행이다.
 */
public record DailyWorkingMinutes(
        Long employeeId,
        LocalDate workDate,
        long workingMinutes
) {
}
