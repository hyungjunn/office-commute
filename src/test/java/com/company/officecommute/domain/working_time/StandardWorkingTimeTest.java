package com.company.officecommute.domain.working_time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StandardWorkingTimeTest {

    @Test
    @DisplayName("평일에서 휴일을 뺀 날 수가 소정근로일이다")
    void countsWeekDaysMinusHolidays() {
        // 2024년 5월: 평일 23일. 5/5는 일요일이라 빠지지 않고, 5/6(대체공휴일·월)과 5/15(수)만 차감된다.
        StandardWorkingTime standardWorkingTime = StandardWorkingTime.of(
                YearMonth.of(2024, 5),
                Set.of(LocalDate.of(2024, 5, 5), LocalDate.of(2024, 5, 6), LocalDate.of(2024, 5, 15)));

        assertThat(standardWorkingTime.workingDays()).isEqualTo(21L);
    }

    @Test
    @DisplayName("소정근로시간은 소정근로일 × 8시간이다")
    void convertsWorkingDaysToMinutes() {
        StandardWorkingTime standardWorkingTime = StandardWorkingTime.of(YearMonth.of(2024, 6), Set.of());

        assertThat(standardWorkingTime.workingDays()).isEqualTo(20L);
        assertThat(standardWorkingTime.workingMinutes()).isEqualTo(20 * 8 * 60L);
    }

    /**
     * 주말 공휴일을 차감하면 기준선이 낮아져 전 직원 초과근무가 과대 집계된다.
     */
    @Test
    @DisplayName("주말과 겹치는 휴일은 두 번 차감하지 않는다")
    void ignoresHolidaysOnWeekend() {
        // 2024-06-06 현충일은 목요일, 2024-06-01은 토요일.
        StandardWorkingTime standardWorkingTime = StandardWorkingTime.of(
                YearMonth.of(2024, 6),
                Set.of(LocalDate.of(2024, 6, 6), LocalDate.of(2024, 6, 1)));

        assertThat(standardWorkingTime.workingDays()).isEqualTo(19L);
    }

    @Test
    @DisplayName("휴일이 하나도 없는 달은 평일 전체가 소정근로일이다")
    void handlesMonthWithoutHoliday() {
        // 4월·11월은 실제로 공휴일이 0개인 달이다.
        StandardWorkingTime standardWorkingTime = StandardWorkingTime.of(YearMonth.of(2024, 4), Set.of());

        assertThat(standardWorkingTime.workingDays()).isEqualTo(22L);
    }

    @Test
    @DisplayName("대상 월 밖의 날짜는 세지 않는다 — 섞여 들어와도 기준선이 낮아지지 않는다")
    void ignoresDatesOutsideTargetMonth() {
        StandardWorkingTime standardWorkingTime = StandardWorkingTime.of(
                YearMonth.of(2024, 6),
                Set.of(LocalDate.of(2024, 6, 6), LocalDate.of(2024, 5, 6), LocalDate.of(2024, 7, 1)));

        assertThat(standardWorkingTime.workingDays()).isEqualTo(19L);
    }
}
