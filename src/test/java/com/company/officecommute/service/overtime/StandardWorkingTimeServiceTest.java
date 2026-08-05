package com.company.officecommute.service.overtime;

import com.company.officecommute.web.HolidayApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StandardWorkingTimeServiceTest {

    @InjectMocks
    private StandardWorkingTimeService standardWorkingTimeService;

    @Mock
    private HolidayApiClient holidayApiClient;

    @Test
    @DisplayName("소정근로일 = 평일 수 − 평일인 공휴일 수")
    void countStandardWorkingDays_subtractsWeekdayHolidays() {
        // 2024-05: 평일 23일. 05-05(일)·05-06(월)·05-15(수) 중 평일 공휴일은 2일.
        given(holidayApiClient.getHolidays(YearMonth.of(2024, 5))).willReturn(Set.of(
                LocalDate.of(2024, 5, 5),
                LocalDate.of(2024, 5, 6),
                LocalDate.of(2024, 5, 15)
        ));

        long numberOfStandardWorkingDays = standardWorkingTimeService.countNumberOfStandardWorkingDays(YearMonth.of(2024, 5));

        assertThat(numberOfStandardWorkingDays).isEqualTo(21L);
    }

    @Test
    @DisplayName("공휴일이 없는 달은 평일 수가 그대로 소정근로일이다")
    void countStandardWorkingDays_returnsWeekdays_whenNoHoliday() {
        given(holidayApiClient.getHolidays(YearMonth.of(2024, 6))).willReturn(Set.of());

        long numberOfStandardWorkingDays = standardWorkingTimeService.countNumberOfStandardWorkingDays(YearMonth.of(2024, 6));

        assertThat(numberOfStandardWorkingDays).isEqualTo(20L);
    }

    @Test
    @DisplayName("주말과 겹치는 공휴일은 소정근로일에서 차감하지 않는다")
    void countStandardWorkingDays_ignoresHolidaysOnWeekend() {
        // 2024-06-06 현충일은 목요일, 2024-06-01은 토요일.
        given(holidayApiClient.getHolidays(YearMonth.of(2024, 6))).willReturn(Set.of(
                LocalDate.of(2024, 6, 6),
                LocalDate.of(2024, 6, 1)
        ));

        long numberOfStandardWorkingDays = standardWorkingTimeService.countNumberOfStandardWorkingDays(YearMonth.of(2024, 6));

        assertThat(numberOfStandardWorkingDays).isEqualTo(19L);
    }

    @Test
    @DisplayName("응답에 있는 날짜는 그대로 공휴일로 센다")
    void countStandardWorkingDays_countsEveryReturnedDate() {
        // 2026-07-17 제헌절(금요일): 2026년부터 공휴일로 재지정되어 응답에 나타난다.
        // 연도별 공휴일 지정 여부는 API가 판단하고, 우리는 응답을 그대로 신뢰한다.
        given(holidayApiClient.getHolidays(YearMonth.of(2026, 7))).willReturn(Set.of(
                LocalDate.of(2026, 7, 17)
        ));

        long numberOfStandardWorkingDays = standardWorkingTimeService.countNumberOfStandardWorkingDays(YearMonth.of(2026, 7));

        // 2026년 7월 평일 23일 − 제헌절 1일.
        assertThat(numberOfStandardWorkingDays).isEqualTo(22L);
    }

    @Test
    @DisplayName("소정근로시간 = 소정근로일 × 8시간")
    void calculateStandardWorkingMinutes_multipliesEightHours() {
        assertThat(standardWorkingTimeService.calculateStandardWorkingMinutes(20L)).isEqualTo(9_600L);
    }
}
