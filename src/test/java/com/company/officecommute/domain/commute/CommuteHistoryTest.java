package com.company.officecommute.domain.commute;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class CommuteHistoryTest {

    @Test
    void testEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, workStartTime, null, 0);

        CommuteHistory commuteHistoryAfterEndWork = commuteHistory.endWork(workEndTime);

        Assertions.assertThat(commuteHistoryAfterEndWork.getWorkingMinutes()).isEqualTo(10L * 60);
    }

    @Test
    void testEndWorkWhenNotStartWork() {
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, null, null, 0);
        Assertions.assertThatThrownBy(() -> commuteHistory.endWork(workEndTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출근을 하지 않은 상태입니다.");
    }

    @Test
    void testEndWorkWhenAlreadyEndWork() {
        ZonedDateTime workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        ZonedDateTime workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        CommuteHistory commuteHistory = new CommuteHistory(1L, 1L, workStartTime, workEndTime, 10L * 60);
        Assertions.assertThatThrownBy(() -> commuteHistory.endWork(workEndTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 퇴근을 했습니다.");
    }
    
}
