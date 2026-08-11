package com.company.officecommute.repository.report;

import com.company.officecommute.domain.report.ReportDispatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.Optional;

public interface ReportDispatchRepository extends JpaRepository<ReportDispatch, Long> {

    Optional<ReportDispatch> findByTargetYearMonth(YearMonth targetYearMonth);
}
