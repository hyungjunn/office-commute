package com.company.officecommute.domain.commute;

import java.time.Duration;
import java.time.ZonedDateTime;

public class CommuteHistory {

    private Long id;

    private Long employeeId;

    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    private long workingMinutes;

    protected CommuteHistory() {
    }

    public CommuteHistory(
            Long id,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.workingMinutes = workingMinutes;
    }

    public CommuteHistory endWork(ZonedDateTime workEndTime) {
        if (this.workStartTime == null) {
            throw new IllegalArgumentException("출근을 하지 않은 상태입니다.");
        }
        Duration duration = Duration.between(this.workStartTime, workEndTime);
        long workingMinutes = duration.toMinutes();
        return new CommuteHistory(this.id, this.employeeId, this.workStartTime, workEndTime, workingMinutes);
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }

}
