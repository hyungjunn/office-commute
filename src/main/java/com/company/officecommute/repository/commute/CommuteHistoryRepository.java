package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface CommuteHistoryRepository extends JpaRepository<CommuteHistory, Long> {
    Optional<CommuteHistory> findFirstByEmployeeIdOrderByWorkStartTimeDesc(Long employeeId);

    List<CommuteHistory> findByEmployeeIdAndWorkStartTimeBetween(Long id, ZonedDateTime startOfMonth, ZonedDateTime endOfMonth);
}
