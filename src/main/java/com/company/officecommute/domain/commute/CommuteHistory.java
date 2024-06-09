package com.company.officecommute.domain.commute;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
public class CommuteHistory {

    @Id
    @GeneratedValue
    private Long commuteHistoryId;

    private Long employeeId;

    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    private long workingMinutes;

    private boolean usingDayOff;

    private static final int ANNUAL_LEAVE_TIME = 0;

    private static final boolean IS_ANNUAL_LEAVE = true;

    protected CommuteHistory() {
    }

    // 연차일 때, 근무 이력을 나타내는 생성자
    public CommuteHistory(Long employeeId) {
        this(null, employeeId, ZonedDateTime.now(), ZonedDateTime.now(), ANNUAL_LEAVE_TIME, IS_ANNUAL_LEAVE);
    }

    public CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes
    ) {
        this(commuteHistoryId, employeeId, workStartTime, workEndTime, workingMinutes, false);
    }

    public CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            ZonedDateTime workStartTime,
            ZonedDateTime workEndTime,
            long workingMinutes,
            boolean usingDayOff
    ) {
        this.commuteHistoryId = commuteHistoryId;
        this.employeeId = employeeId;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.workingMinutes = workingMinutes;
        this.usingDayOff = usingDayOff;
    }

    public CommuteHistory endWork(ZonedDateTime workEndTime) {
        if (this.workStartTime == null) {
            throw new IllegalArgumentException("출근을 하지 않은 상태입니다.");
        }
        if (this.workEndTime != null) {
            throw new IllegalArgumentException("이미 퇴근을 했습니다.");
        }
        Duration duration = Duration.between(this.workStartTime, workEndTime);
        long workingMinutes = duration.toMinutes();
        return new CommuteHistory(this.commuteHistoryId, this.employeeId, this.workStartTime, workEndTime, workingMinutes);
    }

    public Detail toDetail() {
        if (isAnnualLeaveDate()) {
            return new Detail(this.workStartTimeToLocalDate(), ANNUAL_LEAVE_TIME, this.usingDayOff);
        }
        return new Detail(this.workStartTimeToLocalDate(), this.workingMinutes, this.usingDayOff);
    }

    private boolean isAnnualLeaveDate() {
        return this.workStartTime == this.workEndTime;
    }

    public LocalDate workStartTimeToLocalDate() {
        return workStartTime.toLocalDate();
    }

    public boolean endTimeIsNull() {
        return this.workEndTime == null;
    }


    public ZonedDateTime getWorkEndTime() {
        return workEndTime;
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }
}
