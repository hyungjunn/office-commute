package com.company.officecommute.dto.employee.request;

public record EmployeeUpdateTeamNameRequest(
        Long employeeId,
        String teamName
) {
}
