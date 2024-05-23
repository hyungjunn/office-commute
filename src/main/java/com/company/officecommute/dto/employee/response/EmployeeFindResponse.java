package com.company.officecommute.dto.employee.response;

import com.company.officecommute.domain.employee.Employee;

public record EmployeeFindResponse(
        String name,
        String teamName,
        String role,
        String birthday,
        String workStartDate
) {

    public EmployeeFindResponse(String name, String teamName, String role, String birthday, String workStartDate) {
        this.name = name;
        this.teamName = teamName;
        this.role = role;
        this.birthday = birthday;
        this.workStartDate = workStartDate;
    }

    public static EmployeeFindResponse from(Employee employee) {
        return new EmployeeFindResponse(
                employee.getName(),
                employee.getTeamName(),
                employee.getRole().name(),
                employee.getBirthday().toString(),
                employee.getWorkStartDate().toString()
        );
    }
}
