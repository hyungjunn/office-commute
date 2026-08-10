package com.company.officecommute.service.overtime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Set;

/**
 * 근로기준법 제50조·제56조 기준의 월별 초과근무 산정.
 * <p>
 * 1주(월~일) 단위로 계산한다:<br>
 * 연장근로 = Σ 일별 max(0, 실근로 − 8h) + max(0, Σ 일별 min(실근로, 8h) − 40h) —
 * 두 항은 정의상 겹치지 않아 이중가산이 없다.<br>
 * 휴일근로(일요일·공휴일)는 주 40h 산정 기반에서 제외하고 8h 이내/초과로 나눠 따로 집계한다.<br>
 * 월 귀속: 일 단위 값은 그 날이 속한 달, 주 40h 잔여분은 주의 마지막 날(일요일)이 속한 달.
 * <p>
 * 입력 계약: {@code workingMinutesByDate}와 {@code holidays}는
 * {@link #requiredRangeStart(YearMonth)}부터 월 말일까지를 덮어야 한다
 * (첫 주가 전월에 걸치면 전월 말 며칠 포함).
 */
public class MonthlyOverTimeCalculator {

    private static final long DAILY_STANDARD_MINUTES = 8 * 60;
    private static final long WEEKLY_STANDARD_MINUTES = 40 * 60;
    private static final int DAYS_PER_WEEK = 7;

    private MonthlyOverTimeCalculator() {
    }

    /**
     * 이 계산이 소비하는 입력 범위의 시작일 — 대상 월 1일이 속한 주(월~일)의 월요일.
     * <p>
     * 범위 밖 날짜는 {@code getOrDefault(date, 0)}으로 조용히 0분 처리되어 아무 에러 없이
     * 과소 집계되므로, 호출자는 조회·검증 범위를 반드시 이 값에서 시작해야 한다.
     * 주 시작 요일은 취업규칙으로 정하는 사항이라 바뀔 수 있는 값이고, 그때 여기 한 곳만 바꾼다.
     */
    public static LocalDate requiredRangeStart(YearMonth yearMonth) {
        return yearMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static MonthlyOverTime calculate(
            YearMonth yearMonth,
            Map<LocalDate, Long> workingMinutesByDate,
            Set<LocalDate> holidays
    ) {
        long overTimeMinutes = 0;
        long holidayWithin8HoursMinutes = 0;
        long holidayExceeding8HoursMinutes = 0;

        LocalDate firstWeekStart = requiredRangeStart(yearMonth);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        for (LocalDate weekStart = firstWeekStart; !weekStart.isAfter(endOfMonth); weekStart = weekStart.plusWeeks(1)) {
            long weeklyBaseMinutes = 0;
            for (int dayOffset = 0; dayOffset < DAYS_PER_WEEK; dayOffset++) {
                LocalDate date = weekStart.plusDays(dayOffset);
                long minutes = workingMinutesByDate.getOrDefault(date, 0L);
                boolean belongsToMonth = YearMonth.from(date).equals(yearMonth);

                if (isHolidayWork(date, holidays)) {
                    if (belongsToMonth) {
                        holidayWithin8HoursMinutes += Math.min(minutes, DAILY_STANDARD_MINUTES);
                        holidayExceeding8HoursMinutes += Math.max(0, minutes - DAILY_STANDARD_MINUTES);
                    }
                    continue;
                }

                weeklyBaseMinutes += Math.min(minutes, DAILY_STANDARD_MINUTES);
                if (belongsToMonth) {
                    overTimeMinutes += Math.max(0, minutes - DAILY_STANDARD_MINUTES);
                }
            }

            LocalDate weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1);
            if (YearMonth.from(weekEnd).equals(yearMonth)) {
                overTimeMinutes += Math.max(0, weeklyBaseMinutes - WEEKLY_STANDARD_MINUTES);
            }
        }

        return new MonthlyOverTime(overTimeMinutes, holidayWithin8HoursMinutes, holidayExceeding8HoursMinutes);
    }

    private static boolean isHolidayWork(LocalDate date, Set<LocalDate> holidays) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(date);
    }
}
