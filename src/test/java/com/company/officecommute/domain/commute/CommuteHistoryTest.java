package com.company.officecommute.domain.commute;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CommuteHistoryTest {

    private static final String KOREA = "Asia/Seoul";

    @Test
    void testEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        CommuteHistory commuteHistoryAfterEndWork = commuteHistory.endWork(workEndTime.toInstant());

        assertThat(commuteHistoryAfterEndWork.getWorkingMinutes()).isEqualTo(10L * 60);
    }

    @Test
    void testRegisterWorkStartWithoutStartTimeThrows() {
        assertThatThrownBy(() -> CommuteHistory.registerWorkStart(1L, null, ZoneId.of(KOREA)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workStartTime은 null일 수 없습니다");
    }

    @Test
    void testEndWorkWhenAlreadyEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));

        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);
        assertThatThrownBy(() -> commuteHistory.endWork(workEndTime.toInstant()))
                .isInstanceOf(CommuteAlreadyEndedException.class)
                .hasMessage("이미 퇴근 처리된 근무입니다.");
    }

    @Test
    void calculateWorkingMinutes_computesWithoutMutatingState() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        long workingMinutes = commuteHistory.calculateWorkingMinutes(workEndTime.toInstant());

        assertThat(workingMinutes).isEqualTo(10L * 60);
        // 조건부 update가 유일한 쓰기 경로가 되도록 엔티티 상태는 그대로여야 한다
        assertThat(commuteHistory.endTimeIsNull()).isTrue();
        assertThat(commuteHistory.getWorkingMinutes()).isZero();
    }

    @Test
    void calculateWorkingMinutes_throwsWhenAlreadyEnded() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        assertThatThrownBy(() -> commuteHistory.calculateWorkingMinutes(workEndTime.toInstant()))
                .isInstanceOf(CommuteAlreadyEndedException.class)
                .hasMessage("이미 퇴근 처리된 근무입니다.");
    }

    @Test
    void testEndTimeIsNull() {
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, ZonedDateTime.now());

        assertThat(commuteHistory.endTimeIsNull()).isTrue();
    }

    @Test
    void testEndWorkBeforeStartThrows() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime earlierThanStart = ZonedDateTime.of(2024, 1, 1, 7, 30, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThatThrownBy(() -> commuteHistory.endWork(earlierThanStart.toInstant()))
                .isInstanceOf(InvalidCommuteRangeException.class)
                .hasMessage("퇴근 시간이 출근 시간보다 이릅니다.");
    }

    @Test
    void toDailyWorkDuration_workingDate() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        DailyWorkDuration dailyWorkDuration = commuteHistory.toDailyWorkDuration();

        assertThat(dailyWorkDuration.getDate()).isEqualTo(workStartTime.toLocalDate());
        assertThat(dailyWorkDuration.getWorkingMinutes()).isEqualTo(10L * 60);
        assertThat(dailyWorkDuration.isUsingDayOff()).isFalse();
    }

    @Test
    void toDailyWorkDuration_usesWorkDateCalculatedByWorkZone() {
        ZoneId utc = ZoneId.of("UTC");
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 15, 30, 0, 0, utc);
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 8, 1, 1, 0, 0, 0, korea);
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(
                1L, 1L, workStartTime, workEndTime, korea);

        DailyWorkDuration dailyWorkDuration = commuteHistory.toDailyWorkDuration();

        assertThat(commuteHistory.getWorkDate()).isEqualTo(LocalDate.of(2024, 8, 1));
        assertThat(workStartTime.toLocalDate()).isEqualTo(LocalDate.of(2024, 7, 31));
        assertThat(dailyWorkDuration.getDate()).isEqualTo(LocalDate.of(2024, 8, 1));
    }

    @Test
    void status_isCompletedOnceWorkEnded() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(1L, 1L, workStartTime, workEndTime);

        assertThat(commuteHistory.status(workEndTime.plusDays(3).toInstant()))
                .isEqualTo(CommuteStatus.COMPLETED);
    }

    @Test
    void status_isDayOffForAnnualLeave() {
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(
                1L, LocalDate.of(2024, 1, 1), ZoneId.of(KOREA));

        assertThat(commuteHistory.status(Instant.parse("2024-01-01T05:00:00Z")))
                .isEqualTo(CommuteStatus.DAY_OFF);
    }

    @Test
    void status_isInProgressWhileTheWorkDateIsStillTodayInTheWorkZone() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.status(workStartTime.plusHours(5).toInstant()))
                .isEqualTo(CommuteStatus.IN_PROGRESS);
    }

    @Test
    void status_isUnclosedOnceTheWorkDatePassedInTheWorkZone() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.status(workStartTime.plusDays(1).toInstant()))
                .isEqualTo(CommuteStatus.UNCLOSED);
    }

    @Test
    void status_judgesTodayByWorkZoneNotByTheServerOrCallerZone() {
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 2, 9, 0, 0, 0, losAngeles);
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime, losAngeles);

        // LA 기준으로는 아직 근무일(1월 2일) 저녁이지만, UTC 로 읽으면 이미 1월 3일이다.
        Instant stillTheSameWorkDayInLosAngeles = ZonedDateTime
                .of(2024, 1, 2, 18, 0, 0, 0, losAngeles).toInstant();
        assertThat(stillTheSameWorkDayInLosAngeles.atZone(ZoneId.of("UTC")).toLocalDate())
                .isEqualTo(LocalDate.of(2024, 1, 3));

        // UTC 로 판정했다면 "날이 지난 미마감"이 됐을 순간 — workZone 으로 판정하므로 근무 중이다.
        assertThat(commuteHistory.status(stillTheSameWorkDayInLosAngeles))
                .isEqualTo(CommuteStatus.IN_PROGRESS);
    }

    @Test
    void zonedTimes_areRenderedInWorkZoneOffset() {
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 22, 30, 0, 0, korea);
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 8, 1, 6, 0, 0, 0, korea);
        CommuteHistory commuteHistory = CommuteHistoryFixture.ended(
                1L, 1L, workStartTime, workEndTime, korea);

        assertThat(commuteHistory.zonedWorkStartTime()).isEqualTo(workStartTime.toOffsetDateTime());
        // 자정을 넘긴 근무 — 퇴근 시각의 날짜가 workDate 와 다르다는 사실이 응답에 남는다
        assertThat(commuteHistory.zonedWorkEndTime()).isEqualTo(workEndTime.toOffsetDateTime());
        assertThat(commuteHistory.getWorkDate()).isEqualTo(LocalDate.of(2024, 7, 31));
    }

    @Test
    void zonedTimes_useWorkZoneNotTheZoneTheInstantWasCreatedIn() {
        ZoneId utc = ZoneId.of("UTC");
        ZoneId korea = ZoneId.of(KOREA);
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 7, 31, 23, 0, 0, 0, utc);
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime, korea);

        assertThat(commuteHistory.zonedWorkStartTime())
                .isEqualTo(ZonedDateTime.of(2024, 8, 1, 8, 0, 0, 0, korea).toOffsetDateTime());
    }

    @Test
    void zonedWorkEndTime_isNullWhileCommuteIsOpen() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = CommuteHistoryFixture.open(1L, 1L, workStartTime);

        assertThat(commuteHistory.zonedWorkEndTime()).isNull();
    }

    @Test
    void zonedTimes_areNullForAnnualLeave() {
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(
                1L, LocalDate.of(2024, 1, 1), ZoneId.of(KOREA));

        assertThat(commuteHistory.zonedWorkStartTime()).isNull();
        assertThat(commuteHistory.zonedWorkEndTime()).isNull();
    }

    @Test
    void toDailyWorkDuration_AnnualLeaveDate() {
        LocalDate annualLeaveDate = LocalDate.of(2024, 1, 1);
        CommuteHistory commuteHistory = CommuteHistoryFixture.annualLeave(1L, annualLeaveDate, ZoneId.of(KOREA));

        DailyWorkDuration dailyWorkDuration = commuteHistory.toDailyWorkDuration();

        assertThat(dailyWorkDuration.getDate()).isEqualTo(annualLeaveDate);
        assertThat(dailyWorkDuration.getWorkingMinutes()).isEqualTo(0);
        assertThat(dailyWorkDuration.isUsingDayOff()).isTrue();
    }
}
