package com.company.officecommute.dto.commute.request;

import java.time.ZonedDateTime;

public record WorkEndTimeRequest(
        Long employeeId,
        ZonedDateTime workEndTime
) {
}
