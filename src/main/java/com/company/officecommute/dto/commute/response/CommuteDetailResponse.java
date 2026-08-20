package com.company.officecommute.dto.commute.response;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CommuteDetailResponse(
        LocalDate date,
        OffsetDateTime workStartTime,
        OffsetDateTime workEndTime,
        long workingMinutes,
        boolean usingDayOff,
        CommuteStatus status
) {
    public static CommuteDetailResponse from(CommuteHistory commuteHistory, Instant now) {
        return new CommuteDetailResponse(
                commuteHistory.getWorkDate(),
                commuteHistory.zonedWorkStartTime(),
                commuteHistory.zonedWorkEndTime(),
                commuteHistory.getWorkingMinutes(),
                commuteHistory.isUsingDayOff(),
                commuteHistory.status(now)
        );
    }
}
