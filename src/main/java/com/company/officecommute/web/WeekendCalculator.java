package com.company.officecommute.web;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.IntStream;

public class WeekendCalculator {

    // 주말 수를 구하는 메서드
    public static long countNumberOfWeekends(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        return IntStream.rangeClosed(0, yearMonth.lengthOfMonth() - 1)
                .mapToObj(startDate::plusDays)
                .peek(System.out::println)
                .filter(WeekendCalculator::isWeekend)
                .count();
    }

    public static boolean isWeekend(LocalDate date) {
        return isSaturday(date) || isSunday(date);
    }

    private static boolean isSaturday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY;
    }

    private static boolean isSunday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
