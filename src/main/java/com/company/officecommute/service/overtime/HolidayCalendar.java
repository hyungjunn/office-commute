package com.company.officecommute.service.overtime;

import java.time.LocalDate;
import java.util.Set;

public interface HolidayCalendar {

    Set<LocalDate> findHolidays(OverTimePeriod period);
}
