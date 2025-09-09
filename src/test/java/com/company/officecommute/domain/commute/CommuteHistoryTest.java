package com.company.officecommute.domain.commute;

import org.junit.jupiter.api.Test;

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
        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, workStartTime, null, 0);

        CommuteHistory commuteHistoryAfterEndWork = commuteHistory.endWork(workEndTime);

        assertThat(commuteHistoryAfterEndWork.getWorkingMinutes()).isEqualTo(10L * 60);
    }

    @Test
    void testEndWorkWhenNotStartWork() {
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));

        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, null, null, 0);
        assertThatThrownBy(() -> commuteHistory.endWork(workEndTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출근을 하지 않은 상태입니다.");
    }

    @Test
    void testEndWorkWhenAlreadyEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));

        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, workStartTime, workEndTime, 10L * 60);
        assertThatThrownBy(() -> commuteHistory.endWork(workEndTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 퇴근을 했습니다.");
    }

    @Test
    void testEndTimeIsNull() {
        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, ZonedDateTime.now(),null, 0);

        assertThat(commuteHistory.endTimeIsNull()).isTrue();
    }

    @Test
    void toDetail_workingDate() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of(KOREA));
        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, workStartTime, workEndTime, 10L * 60);

        Detail detail = commuteHistory.toDetail();

        assertThat(detail.getDate()).isEqualTo(workStartTime.toLocalDate());
        assertThat(detail.getWorkingMinutes()).isEqualTo(10L * 60);
        assertThat(detail.isUsingDayOff()).isFalse();
    }

    @Test
    void toDetail_AnnualLeaveDate() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of(KOREA));
        // usingDayOff = true 로 적용
        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, workStartTime, workEndTime, 0, true);

        Detail detail = commuteHistory.toDetail();

        assertThat(detail.getDate()).isEqualTo(workStartTime.toLocalDate());
        assertThat(detail.getWorkingMinutes()).isEqualTo(0);
        assertThat(detail.isUsingDayOff()).isTrue();
    }
}
