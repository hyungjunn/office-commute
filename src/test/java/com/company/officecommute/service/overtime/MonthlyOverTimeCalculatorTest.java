package com.company.officecommute.service.overtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근로기준법 산정 방식의 근거를 고정한다.
 * <p>
 * 월 합계 상계(총근로 − 월 소정근로시간)는 법정 산정 방식이 아니다. 가산 대상 연장근로는
 * 1일 8시간 초과분과 1주 40시간 초과분이고(제50조·제56조), 어떤 날의 조퇴가 다른 날의
 * 야근을 상계할 수 없다. 휴일근로(주휴일·공휴일)는 연장근로와 별도 트랙으로,
 * 8시간 이내 1.5배 / 초과 2.0배 가산이며 주 40시간 산정에 포함하지 않는다(제56조②,
 * 2018년 개정으로 연장가산과의 중복 배제 확정).
 * <p>
 * 2024-07은 1일이 월요일이라 주 경계가 월 경계와 일치해 기본 사례에 쓰고,
 * 월 경계 귀속은 7월 말~8월 초에 걸친 주(7/29 월 ~ 8/4 일)로 검증한다.
 */
class MonthlyOverTimeCalculatorTest {

    private static final YearMonth JULY = YearMonth.of(2024, 7);
    private static final Set<LocalDate> NO_HOLIDAYS = Set.of();

    @Test
    @DisplayName("어떤 날의 4시간 야근은 다른 날의 4시간 조퇴로 상계되지 않는다 — 1일 8시간 초과 기준")
    void dailyExcessIsNotOffsetByEarlyLeaveOnAnotherDay() {
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 720L, // 월 12h — 4h 야근
                LocalDate.of(2024, 7, 2), 240L  // 화 4h — 4h 조퇴
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        // 월 합계 상계라면 0이지만, 법적으로는 야근 4h의 가산수당 지급 의무가 남는다
        assertThat(result).isEqualTo(new MonthlyOverTime(240, 0, 0));
    }

    @Test
    @DisplayName("일별 8시간 이내라도 주 40시간을 넘으면 초과분은 연장근로다 — 주 6일 × 7시간 = 42시간")
    void weeklyExcessIsCountedEvenWithoutDailyExcess() {
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 420L, // 월
                LocalDate.of(2024, 7, 2), 420L, // 화
                LocalDate.of(2024, 7, 3), 420L, // 수
                LocalDate.of(2024, 7, 4), 420L, // 목
                LocalDate.of(2024, 7, 5), 420L, // 금
                LocalDate.of(2024, 7, 6), 420L  // 토 — 휴무일이라 휴일근로가 아니라 주 40h 초과 경로
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        assertThat(result).isEqualTo(new MonthlyOverTime(120, 0, 0));
    }

    @Test
    @DisplayName("같은 시간이 일 8시간 초과이자 주 40시간 초과여도 한 번만 가산한다")
    void overlappingDailyAndWeeklyExcessIsCountedOnce() {
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 720L, // 월 12h
                LocalDate.of(2024, 7, 2), 480L,
                LocalDate.of(2024, 7, 3), 480L,
                LocalDate.of(2024, 7, 4), 480L,
                LocalDate.of(2024, 7, 5), 480L  // 주 실근로 44h
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        // 주 40h 기반은 일별 min(실근로, 8h)의 합(= 40h)이므로 월요일 4h가 두 번 잡히지 않는다
        assertThat(result).isEqualTo(new MonthlyOverTime(240, 0, 0));
    }

    @Test
    @DisplayName("토요일(휴무일) 근무는 휴일근로가 아니라 주 40시간 초과 연장근로다")
    void saturdayWorkFlowsIntoWeeklyOvertime() {
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 480L,
                LocalDate.of(2024, 7, 2), 480L,
                LocalDate.of(2024, 7, 3), 480L,
                LocalDate.of(2024, 7, 4), 480L,
                LocalDate.of(2024, 7, 5), 480L,
                LocalDate.of(2024, 7, 6), 480L  // 토 8h
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        assertThat(result).isEqualTo(new MonthlyOverTime(480, 0, 0));
    }

    @Test
    @DisplayName("일요일(주휴일) 근무는 휴일근로로 분리되고 주 40시간 산정에 들어가지 않는다")
    void sundayWorkIsHolidayWorkAndExcludedFromWeeklyBase() {
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 480L,
                LocalDate.of(2024, 7, 2), 480L,
                LocalDate.of(2024, 7, 3), 480L,
                LocalDate.of(2024, 7, 4), 480L,
                LocalDate.of(2024, 7, 5), 480L,
                LocalDate.of(2024, 7, 7), 360L  // 일 6h — 주 실근로 46h지만 연장은 0
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        assertThat(result).isEqualTo(new MonthlyOverTime(0, 360, 0));
    }

    @Test
    @DisplayName("공휴일 근무는 8시간 이내와 초과로 나뉜다 — 초과분은 2.0배 가산 대상")
    void weekdayHolidayWorkIsSplitAtEightHours() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2024, 7, 3)); // 수요일을 공휴일로 가정
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 480L,
                LocalDate.of(2024, 7, 2), 480L,
                LocalDate.of(2024, 7, 3), 600L, // 공휴일 10h
                LocalDate.of(2024, 7, 4), 480L,
                LocalDate.of(2024, 7, 5), 480L
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, holidays);

        assertThat(result).isEqualTo(new MonthlyOverTime(0, 480, 120));
    }

    @Test
    @DisplayName("연차는 주 40시간 기준선을 낮추지 않지만, 연차 주간의 야근은 일 8시간 초과로 그대로 잡힌다")
    void annualLeaveWeekStillAccruesDailyExcess() {
        Map<LocalDate, Long> minutes = Map.of(
                LocalDate.of(2024, 7, 1), 0L,   // 월 연차 (workingMinutes = 0)
                LocalDate.of(2024, 7, 2), 540L, // 화~금 9h
                LocalDate.of(2024, 7, 3), 540L,
                LocalDate.of(2024, 7, 4), 540L,
                LocalDate.of(2024, 7, 5), 540L
        );

        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        // 월 소정근로시간 차감 방식이라면 연차 8h 결손이 야근 4h를 지웠다 (TODO 3의 왜곡).
        // 실근로 기준에서는 연차가 40h 산정에 포함되지 않을 뿐, 일별 초과 4h가 그대로 남는다.
        assertThat(result).isEqualTo(new MonthlyOverTime(240, 0, 0));
    }

    @Test
    @DisplayName("월 경계에 걸친 주 — 일별 초과분은 그 날이 속한 달에 귀속된다")
    void straddlingWeekDailyExcessBelongsToItsOwnMonth() {
        // 7/29(월)~8/4(일) 주: 7월 사흘 각 10h + 8월 이틀 각 10h + 토 8h
        Map<LocalDate, Long> minutes = straddlingWeekMinutes();

        MonthlyOverTime july = MonthlyOverTimeCalculator.calculate(JULY, minutes, NO_HOLIDAYS);

        // 7/29~31 각 2h 초과 = 6h. 주 40h 잔여분은 주가 8/4에 끝나므로 7월에 실리지 않는다
        assertThat(july).isEqualTo(new MonthlyOverTime(360, 0, 0));
    }

    @Test
    @DisplayName("월 경계에 걸친 주 — 주 40시간 잔여분은 주가 끝나는 달에 귀속된다")
    void straddlingWeekWeeklyRemainderBelongsToWeekEndMonth() {
        Map<LocalDate, Long> minutes = straddlingWeekMinutes();

        MonthlyOverTime august = MonthlyOverTimeCalculator.calculate(YearMonth.of(2024, 8), minutes, NO_HOLIDAYS);

        // 8/1~2 일별 초과 4h + 주간 잔여 8h(기반 48h − 40h, 전월 사흘 포함 주 전체로 판정).
        // 7월분(6h)과 합치면 매 시간이 정확히 한 번 집계된다
        assertThat(august).isEqualTo(new MonthlyOverTime(720, 0, 0));
    }

    @Test
    @DisplayName("입력 범위 시작일은 계산기가 소유한다 — 월 1일이 속한 주의 월요일")
    void requiredRangeStartIsMondayOfFirstWeek() {
        // 조회가 이 날짜보다 늦게 시작하면 누락일이 조용히 0분 처리되어 첫 주 40시간 기반이 과소 집계된다.
        // 호출자(OverTimeService의 조회·미마감 검사)가 이 헬퍼를 공유해 범위 불일치를 컴파일 타임에 막는다.
        assertThat(MonthlyOverTimeCalculator.requiredRangeStart(YearMonth.of(2024, 8)))
                .isEqualTo(LocalDate.of(2024, 7, 29)); // 8/1(목)이 속한 주의 월요일 — 전월로 스필오버
        assertThat(MonthlyOverTimeCalculator.requiredRangeStart(JULY))
                .isEqualTo(LocalDate.of(2024, 7, 1));  // 1일이 월요일이면 확장 없음
    }

    @Test
    @DisplayName("기록이 없으면 모든 트랙이 0이다")
    void noRecordsMeansZero() {
        MonthlyOverTime result = MonthlyOverTimeCalculator.calculate(JULY, Map.of(), NO_HOLIDAYS);

        assertThat(result).isEqualTo(new MonthlyOverTime(0, 0, 0));
    }

    private static Map<LocalDate, Long> straddlingWeekMinutes() {
        return Map.of(
                LocalDate.of(2024, 7, 29), 600L, // 월 10h
                LocalDate.of(2024, 7, 30), 600L, // 화 10h
                LocalDate.of(2024, 7, 31), 600L, // 수 10h
                LocalDate.of(2024, 8, 1), 600L,  // 목 10h
                LocalDate.of(2024, 8, 2), 600L,  // 금 10h
                LocalDate.of(2024, 8, 3), 480L   // 토 8h — 주 실근로 56h
        );
    }
}
