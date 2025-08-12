package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.service.overtime.TotalWorkingMinutes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface CommuteHistoryRepository extends JpaRepository<CommuteHistory, Long> {

    Optional<CommuteHistory> findFirstByEmployeeIdOrderByWorkStartTimeDesc(Long employeeId);

    List<CommuteHistory> findAllByEmployeeIdAndWorkStartTimeBetween(Long id, ZonedDateTime startOfMonth, ZonedDateTime endOfMonth);

    @Query("""
            SELECT new com.company.officecommute.service.overtime.TotalWorkingMinutes(
                        ch.employeeId, e.name, SUM(ch.workingMinutes)
                    )
            FROM CommuteHistory ch
            JOIN Employee e ON ch.employeeId = e.employeeId
            WHERE ch.workStartTime BETWEEN :startOfMonth AND :endOfMonth
            GROUP BY ch.employeeId, e.name
            """)
    List<TotalWorkingMinutes> findWithEmployeeIdByDateRange(ZonedDateTime startOfMonth, ZonedDateTime endOfMonth);

    List<CommuteHistory> findAllByEmployeeId(Long employeeId);
}
