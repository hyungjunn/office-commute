package com.company.officecommute.dto.commute.request;

import java.time.YearMonth;

public record WorkDurationPerDateRequest(
        long employeeId,
        YearMonth yearMonth
) {
}
