package com.company.officecommute.domain.annual_leave;

import java.time.LocalDate;

public class AnnualLeave {

    private Long id;
    private Long employeeId;
    private LocalDate wantedDate;

    public AnnualLeave(Long id, Long employeeId, LocalDate wantedDate) {
        if (wantedDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(String.format("(%s)는 지난 날짜입니다.", wantedDate));
        }
        this.id = id;
        this.employeeId = employeeId;
        this.wantedDate = wantedDate;
    }
}
