package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HolidayJudgmentTest {

    private static final LocalDate NEW_YEAR = LocalDate.of(2026, 1, 1);
    private static final LocalDate FOUNDATION_DAY = LocalDate.of(2026, 5, 20);

    @Test
    @DisplayName("행이 하나도 없는 날짜는 휴일이 아니다")
    void noRowsMeansWorkingDay() {
        assertThat(HolidayJudgment.holidayDatesOf(List.of())).isEmpty();
    }

    @Test
    @DisplayName("is_holiday=true 행이 하나라도 있으면 휴일이다")
    void anyPositiveRowMakesHoliday() {
        assertThat(HolidayJudgment.holidayDatesOf(List.of(Holiday.fromApi(NEW_YEAR, "1월1일"))))
                .containsExactly(NEW_YEAR);
        assertThat(HolidayJudgment.holidayDatesOf(List.of(Holiday.companyHoliday(FOUNDATION_DAY, "창립기념일"))))
                .containsExactly(FOUNDATION_DAY);
        assertThat(HolidayJudgment.holidayDatesOf(List.of(Holiday.manualHoliday(NEW_YEAR, "임시공휴일"))))
                .containsExactly(NEW_YEAR);
    }

    @Test
    @DisplayName("부정 오버라이드는 같은 날짜의 API 행을 이긴다 — 그것이 관리자가 API 오류를 되돌리는 유일한 수단이다")
    void negativeOverrideBeatsApiRow() {
        List<Holiday> rows = List.of(
                Holiday.fromApi(NEW_YEAR, "1월1일"),
                Holiday.manualWorkingDay(NEW_YEAR, "정상 근무(API 오적재 보정)")
        );

        assertThat(HolidayJudgment.holidayDatesOf(rows)).isEmpty();
    }

    @Test
    @DisplayName("부정 오버라이드는 회사 지정 휴일도 이긴다")
    void negativeOverrideBeatsCompanyRow() {
        List<Holiday> rows = List.of(
                Holiday.companyHoliday(FOUNDATION_DAY, "창립기념일"),
                Holiday.manualWorkingDay(FOUNDATION_DAY, "올해는 정상 근무")
        );

        assertThat(HolidayJudgment.holidayDatesOf(rows)).isEmpty();
    }

    @Test
    @DisplayName("판정은 날짜별로 독립이다 — 한 날의 부정 오버라이드가 다른 날에 번지지 않는다")
    void judgesEachDateIndependently() {
        List<Holiday> rows = List.of(
                Holiday.fromApi(NEW_YEAR, "1월1일"),
                Holiday.manualWorkingDay(NEW_YEAR, "정상 근무"),
                Holiday.companyHoliday(FOUNDATION_DAY, "창립기념일")
        );

        assertThat(HolidayJudgment.holidayDatesOf(rows)).containsExactly(FOUNDATION_DAY);
    }
}
