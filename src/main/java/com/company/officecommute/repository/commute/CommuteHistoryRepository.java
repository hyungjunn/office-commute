package com.company.officecommute.repository.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommuteHistoryRepository extends JpaRepository<CommuteHistory, Long> {
    Optional<CommuteHistory> findLatestWorkStartTimeByEmployeeId(Long id);
}
