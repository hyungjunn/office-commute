package com.company.officecommute.dto.annual_leave.request;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public record AnnualLeaveEnrollRequest(

        @NotEmpty(message = "신청할 연차 일자는 필수입니다.")
        List<@NotNull(message = "연차 일자는 null일 수 없습니다.") LocalDate> wantedDates
) {

    public List<AnnualLeave> datesToAnnualLeaves(Long employeeId) {
        return wantedDates.stream()
                .map(wantedDate -> new AnnualLeave(employeeId, wantedDate))
                .collect(Collectors.toList());
    }
}
