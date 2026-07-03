package com.company.officecommute.dto.annual_leave.response;

import com.company.officecommute.domain.annual_leave.AnnualLeave;

import java.time.LocalDate;
import java.util.List;

public record AnnualLeaveGetRemainingResponse(
        Long employeeId,
        List<RemainingLeave> remainingLeaves
) {

    public record RemainingLeave(
            Long id,
            Long employeeId,
            LocalDate wantedDate
    ) {

        public static RemainingLeave from(AnnualLeave annualLeave) {
            return new RemainingLeave(
                    annualLeave.getId(),
                    annualLeave.getEmployeeId(),
                    annualLeave.getWantedDate()
            );
        }
    }
}
