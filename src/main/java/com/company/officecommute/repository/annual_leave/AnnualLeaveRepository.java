package com.company.officecommute.repository.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnualLeaveRepository extends JpaRepository<AnnualLeave, Long> {
    List<AnnualLeave> findByEmployeeId(Long employeeId);
}
