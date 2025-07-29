package com.company.officecommute.dto.employee.request;

import com.company.officecommute.domain.employee.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record EmployeeSaveRequest(
        @NotBlank(message = "직원 이름은 필수입니다.")
        String name,
        @NotNull(message = "역할은 필수입니다.")
        Role role,
        @NotNull(message = "생일은 필수입니다.")
        @Past(message = "생일은 과거 날짜여야 합니다.")
        LocalDate birthday,
        @NotNull(message = "입사일은 필수입니다.")
        @PastOrPresent(message = "입사일은 오늘이거나 과거 날짜여야 합니다.")
        LocalDate workStartDate
) {
}
