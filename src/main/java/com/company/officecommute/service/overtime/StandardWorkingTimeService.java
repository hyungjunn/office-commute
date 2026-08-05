package com.company.officecommute.service.overtime;

import com.company.officecommute.web.HolidayApiClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

@Service
public class StandardWorkingTimeService {

    private final HolidayApiClient holidayApiClient;

    public StandardWorkingTimeService(HolidayApiClient holidayApiClient) {
        this.holidayApiClient = holidayApiClient;
    }

    public long countNumberOfStandardWorkingDays(YearMonth yearMonth) {
        Set<LocalDate> holidays = holidayApiClient.getHolidays(yearMonth);
        long numberOfWeekDays = getNumberOfWeekDays(yearMonth);
        long numberOfHolidays = countWeekdayHolidays(holidays);

        return numberOfWeekDays - numberOfHolidays;
    }

    public long calculateStandardWorkingMinutes(long numberOfStandardWorkingDays) {
        return numberOfStandardWorkingDays * 8 * 60;
    }

    private static long getNumberOfWeekDays(YearMonth yearMonth) {
        int lengthOfMonth = yearMonth.lengthOfMonth();
        long numberOfWeekends = WeekendCalculator.countNumberOfWeekends(yearMonth);
        return lengthOfMonth - numberOfWeekends;
    }

    private long countWeekdayHolidays(Set<LocalDate> holidays) {
        return holidays.stream()
                .filter(date -> !WeekendCalculator.isWeekend(date))
                .count();
    }

}
