package com.company.officecommute.repository.holiday;

import com.company.officecommute.domain.holiday.HolidayMonthMarker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.YearMonth;

public interface HolidayMonthMarkerRepository extends JpaRepository<HolidayMonthMarker, LocalDate> {

    default boolean existsByMonth(YearMonth month) {
        return existsById(month.atDay(1));
    }
}
