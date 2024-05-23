package com.company.officecommute.dto.employee.request;

import com.company.officecommute.domain.employee.Role;

import java.time.LocalDate;

public record EmployeeSaveRequest(
        String name,
        Role role,
        LocalDate birthday,
        LocalDate workStartDate
) {
}
