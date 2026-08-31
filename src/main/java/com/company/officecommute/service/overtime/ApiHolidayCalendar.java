package com.company.officecommute.service.overtime;

import com.company.officecommute.web.HolidayApiClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

@Component
public class ApiHolidayCalendar implements HolidayCalendar {

    private final HolidayApiClient holidayApiClient;

    public ApiHolidayCalendar(HolidayApiClient holidayApiClient) {
        this.holidayApiClient = holidayApiClient;
    }

    @Override
    public Set<LocalDate> findHolidays(OverTimePeriod period) {
        Set<LocalDate> holidays = new HashSet<>();
        for (YearMonth month : period.requiredHolidayMonths()) {
            holidays.addAll(holidayApiClient.getHolidays(month));
        }
        return holidays;
    }
}
