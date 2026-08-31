package com.company.officecommute.service.overtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 초과근무 산정이 소비하는 기간을 이 값 객체 하나가 소유한다.
 * <p>
 * 계산({@link MonthlyOverTimeCalculator})·근무 기록 조회({@link OverTimeSnapshotReader})·
 * 공휴일 조회·미마감 검사({@link OverTimeService})가 각자 범위를 계산하면, 조회가 계산보다
 * 좁아진 순간 빠진 날이 조용히 0분으로 처리되어 에러 없이 과소 집계된다(= 임금 미지급).
 * 주 시작 요일은 취업규칙으로 정하는 사항이라 바뀔 수 있고, 그때 바꿀 곳도 여기 하나다.
 */
class OverTimePeriodTest {

    // 2024-08-01은 목요일 — 1일이 속한 주가 7월에 걸친다
    private static final YearMonth AUGUST = YearMonth.of(2024, 8);
    // 2024-07-01은 월요일 — 주 경계와 월 경계가 일치한다
    private static final YearMonth JULY = YearMonth.of(2024, 7);

    @Test
    @DisplayName("범위 시작일은 대상 월 1일이 속한 주의 월요일 — 첫 주가 전월에 걸치면 전월로 넘어간다")
    void rangeStartIsMondayOfFirstWeek() {
        assertThat(new OverTimePeriod(AUGUST).rangeStart()).isEqualTo(LocalDate.of(2024, 7, 29));
        assertThat(new OverTimePeriod(JULY).rangeStart()).isEqualTo(LocalDate.of(2024, 7, 1));
    }

    @Test
    @DisplayName("범위 종료일은 대상 월 말일 — 다음 달로 걸친 주는 그 달 리포트가 집계한다")
    void rangeEndIsEndOfTargetMonth() {
        assertThat(new OverTimePeriod(AUGUST).rangeEnd()).isEqualTo(LocalDate.of(2024, 8, 31));
        assertThat(new OverTimePeriod(JULY).rangeEnd()).isEqualTo(LocalDate.of(2024, 7, 31));
    }

    @Test
    @DisplayName("첫 주가 전월에 걸치면 전월 공휴일도 필요하다 — 그 주의 휴일근로 분류에 쓰인다")
    void requiredHolidayMonthsIncludeStraddlingMonth() {
        assertThat(new OverTimePeriod(AUGUST).requiredHolidayMonths())
                .containsExactlyInAnyOrder(AUGUST, JULY);
    }

    @Test
    @DisplayName("월 1일이 월요일이면 공휴일도 그 달만 필요하다 — 불필요한 외부 API 호출을 만들지 않는다")
    void requiredHolidayMonthsAreSingleWhenWeekAlignsWithMonth() {
        assertThat(new OverTimePeriod(JULY).requiredHolidayMonths()).containsExactly(JULY);
    }
}
