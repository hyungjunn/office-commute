package com.company.officecommute.dto.annual_leave.response;

import java.time.LocalDate;

public record AnnualLeaveEnrollmentResponse(
        Long annualLeaveId,
        LocalDate enrolledDate
) {
}
