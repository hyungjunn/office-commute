package com.company.officecommute.dto.annual_leave.request;

import com.company.officecommute.domain.annual_leave.AnnualLeave;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public record AnnualLeaveEnrollRequest(
        Long employeeId,
        List<LocalDate> wantedDates
) {
    public List<AnnualLeave> datesToAnnualLeaves() {
        return wantedDates.stream()
                .map(wantedDate -> new AnnualLeave(employeeId(), wantedDate))
                .collect(Collectors.toList());
    }
}
