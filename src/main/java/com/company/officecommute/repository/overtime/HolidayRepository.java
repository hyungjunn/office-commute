package com.company.officecommute.repository.overtime;

import com.company.officecommute.domain.overtime.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    @Query("""
            SELECT h.holidayDate
            FROM Holiday h
            WHERE h.year = :yearValue AND h.month = :monthValue
            """)
    List<LocalDate> findHolidayDatesByYearAndMonth(@Param("yearValue") int year, @Param("monthValue") int month);

    void deleteByYearAndMonth(int year, int month);
}
