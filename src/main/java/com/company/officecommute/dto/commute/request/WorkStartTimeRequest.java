package com.company.officecommute.dto.commute.request;

import java.time.ZonedDateTime;

public record WorkStartTimeRequest(
        Long employeeId,
        ZonedDateTime workStartTime
) {
}
