package com.company.officecommute.repository.holiday;

import com.company.officecommute.domain.holiday.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, LocalDate> {

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate startInclusive, LocalDate endInclusive);
}
