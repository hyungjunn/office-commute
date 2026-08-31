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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ApiHolidayCalendarTest {

    // 2024-08-01은 목요일 — 1일이 속한 주가 7월에 걸친다
    private static final YearMonth AUGUST = YearMonth.of(2024, 8);
    // 2024-07-01은 월요일 — 주 경계와 월 경계가 일치한다
    private static final YearMonth JULY = YearMonth.of(2024, 7);

    @InjectMocks
    private ApiHolidayCalendar apiHolidayCalendar;

    @Mock
    private HolidayApiClient holidayApiClient;

    @Test
    @DisplayName("월 1일이 속한 주가 전월에 걸치면 전월 공휴일도 함께 가져온다")
    void findHolidays_fetchesStraddlingMonthHolidays() {
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());

        apiHolidayCalendar.findHolidays(new OverTimePeriod(AUGUST));

        // 8/1(목)이 속한 주의 월요일은 7/29 — 그 주의 휴일근로 분류에 전월 공휴일이 필요하다
        then(holidayApiClient).should().getHolidays(AUGUST);
        then(holidayApiClient).should().getHolidays(JULY);
    }

    @Test
    @DisplayName("월 1일이 월요일이면 해당 월 공휴일만 가져온다")
    void findHolidays_singleMonthWhenWeekAlignsWithMonth() {
        given(holidayApiClient.getHolidays(any(YearMonth.class))).willReturn(Set.of());

        apiHolidayCalendar.findHolidays(new OverTimePeriod(JULY));

        then(holidayApiClient).should().getHolidays(JULY);
        then(holidayApiClient).should(never()).getHolidays(YearMonth.of(2024, 6));
    }

    @Test
    @DisplayName("걸친 두 달의 공휴일을 하나의 집합으로 합친다")
    void findHolidays_mergesHolidaysOfBothMonths() {
        LocalDate julyHoliday = LocalDate.of(2024, 7, 31);
        LocalDate augustHoliday = LocalDate.of(2024, 8, 15);
        given(holidayApiClient.getHolidays(JULY)).willReturn(Set.of(julyHoliday));
        given(holidayApiClient.getHolidays(AUGUST)).willReturn(Set.of(augustHoliday));

        assertThat(apiHolidayCalendar.findHolidays(new OverTimePeriod(AUGUST)))
                .containsExactlyInAnyOrder(julyHoliday, augustHoliday);
    }
}
