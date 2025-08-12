package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnnualLeaveService {

    private static final Logger log = LoggerFactory.getLogger(AnnualLeaveService.class);

    private final EmployeeRepository employeeRepository;
    private final AnnualLeaveRepository annualLeaveRepository;
    private final CommuteHistoryRepository commuteHistoryRepository;

    public AnnualLeaveService(
            EmployeeRepository employeeRepository,
            AnnualLeaveRepository annualLeaveRepository,
            CommuteHistoryRepository commuteHistoryRepository) {
        this.employeeRepository = employeeRepository;
        this.annualLeaveRepository = annualLeaveRepository;
        this.commuteHistoryRepository = commuteHistoryRepository;
    }

    @Transactional
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(Long employeeId, List<LocalDate> wantedDates) {
        log.info("연차 신청 시작 - employeeId: {}", employeeId);
        Employee employee = employeeRepository.findByEmployeeIdWithTeam(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직원입니다."));
        List<AnnualLeave> existingAnnualLeaves = annualLeaveRepository.findByEmployeeId(employeeId);
        List<AnnualLeave> enrolledLeaves = employee.enrollAnnualLeave(wantedDates, existingAnnualLeaves);

        List<AnnualLeave> savedLeaves = annualLeaveRepository.saveAll(enrolledLeaves);

        List<CommuteHistory> commuteHistories = savedLeaves.stream()
                .map(annualLeave -> new CommuteHistory(employeeId, annualLeave.getWantedDate()))
                .toList();
        commuteHistoryRepository.saveAll(commuteHistories);

        log.info("연차 신청 완료 - employeeId: {}, 신청한 연차 수: {}", employeeId, savedLeaves.size());
        return new AnnualLeaves(savedLeaves).toAnnualLeaveEnrollmentResponse();
    }

    @Transactional(readOnly = true)
    public AnnualLeaveGetRemainingResponse getRemainingAnnualLeaves(Long employeeId) {
        List<AnnualLeave> remainingLeaves = annualLeaveRepository.findByEmployeeId(employeeId)
                .stream()
                .filter(AnnualLeave::isRemain)
                .toList();

        return new AnnualLeaveGetRemainingResponse(employeeId, remainingLeaves);
    }
}