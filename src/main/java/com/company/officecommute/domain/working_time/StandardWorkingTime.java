package com.company.officecommute.domain.working_time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

/**
 * 한 달의 소정근로시간 — 초과근무를 재는 기준선이다.
 * <p>
 * 평일에서 휴일을 뺀 날 수에 1일 8시간을 곱한다. 주말과 겹치는 휴일을 빼지 않는 것이 핵심이다 —
 * 이미 평일이 아니므로 두 번 차감하면 기준선이 낮아져 초과근무가 과대 집계된다.
 * <p>
 * 휴일 판정은 이 클래스의 일이 아니다. 어떤 날이 휴일인지는 공휴일 원장이 정하고, 여기는
 * 그 결과를 받아 달력 산수만 한다.
 */
public record StandardWorkingTime(long workingDays, long workingMinutes) {

    // TODO: 1일 소정근로시간도 본래 직원별 속성(단시간 근로자·시차출퇴근). 현재 전 직원 8시간으로 단순화.
    private static final long MINUTES_PER_WORKING_DAY = 8 * 60;

    public static StandardWorkingTime of(YearMonth yearMonth, Set<LocalDate> holidayDates) {
        long workingDays = countWeekDays(yearMonth) - countWeekDayHolidays(yearMonth, holidayDates);
        return new StandardWorkingTime(workingDays, workingDays * MINUTES_PER_WORKING_DAY);
    }

    private static long countWeekDays(YearMonth yearMonth) {
        return yearMonth.lengthOfMonth() - WeekendCalculator.countNumberOfWeekends(yearMonth);
    }

    /**
     * 대상 월 밖의 날짜는 세지 않는다. 호출자가 월 단위로 조회해 넘기는 것이 전제지만,
     * 그 전제가 깨지면 기준선이 조용히 낮아져 초과근무가 과대 집계되므로 여기서도 막는다.
     */
    private static long countWeekDayHolidays(YearMonth yearMonth, Set<LocalDate> holidayDates) {
        return holidayDates.stream()
                .filter(date -> YearMonth.from(date).equals(yearMonth))
                .filter(date -> !WeekendCalculator.isWeekend(date))
                .count();
    }
}
