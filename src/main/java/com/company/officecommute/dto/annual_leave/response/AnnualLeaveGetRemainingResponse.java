package com.company.officecommute.dto.annual_leave.response;

import com.company.officecommute.domain.annual_leave.AnnualLeave;

import java.util.List;

public record AnnualLeaveGetRemainingResponse(
        Long employeeId,
        List<AnnualLeave> remainingLeaves
) {
}
