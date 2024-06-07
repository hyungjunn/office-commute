package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CommuteHistoryRepository extends JpaRepository<CommuteHistory, Long> {
    Optional<CommuteHistory> findFirstByEmployeeIdOrderByWorkStartTimeDesc(Long employeeId);

    List<CommuteHistory> findByEmployeeIdAndWorkStartTimeBetween(Long id, ZonedDateTime startOfMonth, ZonedDateTime endOfMonth);

    @Query("""
            SELECT ch.employeeId AS employeeId, SUM(ch.workingMinutes) AS totalWorkingMinutes
            FROM CommuteHistory ch
            WHERE ch.workStartTime BETWEEN :startOfMonth AND :endOfMonth
            GROUP BY ch.employeeId
            """)
    Map<Long, Long> findWorkingMinutesTimeByEmployeeAndDateRange(ZonedDateTime startOfMonth, ZonedDateTime endOfMonth);
}
