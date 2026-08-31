package com.company.officecommute.dto.overtime.response;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.service.overtime.MonthlyOverTime;

public record OverTimeCalculateResponse(
        Long id,
        String employeeCode,
        String name,
        String teamName,
        long overTimeMinutes,
        long holidayWithin8HoursMinutes,
        long holidayExceeding8HoursMinutes
) {
    private static final String UNASSIGNED_TEAM_NAME = "미배정";

    public static OverTimeCalculateResponse from(Employee employee, MonthlyOverTime overTime) {
        return new OverTimeCalculateResponse(
                employee.getEmployeeId(),
                employee.getEmployeeCode(),
                employee.getName(),
                employee.getTeamName() != null ? employee.getTeamName() : UNASSIGNED_TEAM_NAME,
                overTime.overTimeMinutes(),
                overTime.holidayWithin8HoursMinutes(),
                overTime.holidayExceeding8HoursMinutes()
        );
    }
}
