package com.company.officecommute.domain.commute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_commute_history_employee_date", columnNames = {"employee_id", "work_date"})
})
public class CommuteHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commuteHistoryId;

    private Long employeeId;

    // Instant는 JDBC 바인딩이 항상 UTC로 정규화되므로 JVM 기본 타임존에 의존하지 않는다.
    // 달력 해석(날짜·표시)은 workZone과 조합해서만 한다.
    @Column(nullable = false)
    private Instant workStartTime;

    private Instant workEndTime;

    private long workingMinutes;

    private boolean usingDayOff;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "work_zone", nullable = false)
    private String workZone;

    private static final int ANNUAL_LEAVE_TIME = 0;

    private static final boolean IS_ANNUAL_LEAVE = true;

    protected CommuteHistory() {
    }

    public static CommuteHistory registerWorkStart(Long employeeId, Instant workStartTime, ZoneId workZone) {
        return new CommuteHistory(null, employeeId, workStartTime, null, 0, false, workZone);
    }

    public static CommuteHistory registerAnnualLeave(Long employeeId, LocalDate annualLeaveDate, ZoneId workZone) {
        Instant startOfDay = annualLeaveDate.atStartOfDay(workZone).toInstant();
        return new CommuteHistory(
                null,
                employeeId,
                startOfDay,
                startOfDay,
                ANNUAL_LEAVE_TIME,
                IS_ANNUAL_LEAVE,
                workZone
        );
    }

    private CommuteHistory(
            Long commuteHistoryId,
            Long employeeId,
            Instant workStartTime,
            Instant workEndTime,
            long workingMinutes,
            boolean usingDayOff,
            ZoneId workZone
    ) {
        Objects.requireNonNull(workStartTime, "workStartTime은 null일 수 없습니다");
        Objects.requireNonNull(workZone, "workZone은 null일 수 없습니다");
        this.commuteHistoryId = commuteHistoryId;
        this.employeeId = employeeId;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.workingMinutes = workingMinutes;
        this.usingDayOff = usingDayOff;
        this.workZone = workZone.getId();
        this.workDate = workStartTime.atZone(workZone).toLocalDate();
    }

    public CommuteHistory endWork(Instant workEndTime) {
        this.workingMinutes = calculateWorkingMinutes(workEndTime);
        this.workEndTime = workEndTime;
        return this;
    }

    // 상태를 변경하지 않는다 — managed 엔티티에서 호출해도 dirty checking flush가 발생하지 않아야
    // 조건부 update(workEndTime IS NULL)가 유일한 쓰기 경로로 유지된다.
    public long calculateWorkingMinutes(Instant workEndTime) {
        if (this.workEndTime != null) {
            throw new CommuteAlreadyEndedException();
        }
        if (workEndTime.isBefore(this.workStartTime)) {
            throw new InvalidCommuteRangeException();
        }
        long workingMinutes = Duration.between(this.workStartTime, workEndTime).toMinutes();
        WorkingMinutes validatedWorkingMinutes = new WorkingMinutes(workingMinutes);
        return validatedWorkingMinutes.getWorkingMinutes();
    }

    public DailyWorkDuration toDailyWorkDuration() {
        if (isAnnualLeaveDate()) {
            return new DailyWorkDuration(this.workDate, ANNUAL_LEAVE_TIME, this.usingDayOff);
        }
        return new DailyWorkDuration(this.workDate, this.workingMinutes, this.usingDayOff);
    }

    // "오늘"은 기록 자신의 workZone 으로 판정한다 — workDate 도 같은 zone 에서 파생됐으므로
    // 양변이 같은 달력 위에 놓인다. 호출자(브라우저·JVM)의 기본 타임존은 개입하지 않는다.
    public CommuteStatus status(Instant now) {
        if (isAnnualLeaveDate()) {
            return CommuteStatus.DAY_OFF;
        }
        if (this.workEndTime != null) {
            return CommuteStatus.COMPLETED;
        }
        LocalDate todayInWorkZone = now.atZone(ZoneId.of(this.workZone)).toLocalDate();
        if (this.workDate.isBefore(todayInWorkZone)) {
            return CommuteStatus.UNCLOSED;
        }
        return CommuteStatus.IN_PROGRESS;
    }

    // 연차는 workStartTime/workEndTime이 자정으로 합성된 값이라 표시할 출퇴근 시각이 없다.
    public OffsetDateTime zonedWorkStartTime() {
        if (isAnnualLeaveDate()) {
            return null;
        }
        return toWorkZone(this.workStartTime);
    }

    public OffsetDateTime zonedWorkEndTime() {
        if (isAnnualLeaveDate() || this.workEndTime == null) {
            return null;
        }
        return toWorkZone(this.workEndTime);
    }

    private OffsetDateTime toWorkZone(Instant instant) {
        return instant.atZone(ZoneId.of(this.workZone)).toOffsetDateTime();
    }

    private boolean isAnnualLeaveDate() {
        return this.usingDayOff;
    }

    public Long getCommuteHistoryId() {
        return commuteHistoryId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public boolean isUsingDayOff() {
        return usingDayOff;
    }

    public boolean endTimeIsNull() {
        return this.workEndTime == null;
    }


    public Instant getWorkEndTime() {
        return workEndTime;
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }

    public String getWorkZone() {
        return workZone;
    }
}
