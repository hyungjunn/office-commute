package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.service.employee.EmployeeDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class CommuteHistoryService {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeDomainService employeeDomainService;
    private final CommuteHistoryDomainService commuteHistoryDomainService;

    public CommuteHistoryService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeDomainService employeeDomainService,
            CommuteHistoryDomainService commuteHistoryDomainService
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeDomainService = employeeDomainService;
        this.commuteHistoryDomainService = commuteHistoryDomainService;
    }

    @Transactional
    public void registerWorkStartTime(Long employeeId) {
        Employee employee = employeeDomainService.findEmployeeById(employeeId);

        // 이 직원이 퇴근을 했는지 안했는지 확인 후 퇴근을 안했으면 예외를 발생시킨다. (출근을 할 수 없음)
        commuteHistoryDomainService.distinguishItIsPossibleToWork(employee.getEmployeeId());

        commuteHistoryRepository.save(
                new CommuteHistory(
                        null,
                        employee.getEmployeeId(),
                        ZonedDateTime.now(),
                        null,
                        0)
        );
    }

    @Transactional
    public void registerWorkEndTime(Long employeeId, ZonedDateTime workEndTime) {
        Employee employee = employeeDomainService.findEmployeeById(employeeId);
        CommuteHistory lastCommute = commuteHistoryDomainService.findFirstByDomainService(employee.getEmployeeId());
        CommuteHistory commuteHistory = lastCommute.endWork(workEndTime);
        commuteHistoryRepository.save(commuteHistory);
    }

    @Transactional(readOnly = true)
    public WorkDurationPerDateResponse getWorkDurationPerDate(Long employeeId, YearMonth yearMonth) {
        Employee employee = employeeDomainService.findEmployeeById(employeeId);
        List<CommuteHistory> histories = commuteHistoryDomainService.findCommuteHistoriesByEmployeeIdAndMonth(
                employee.getEmployeeId(), yearMonth);
        return new CommuteHistories(histories).toWorkDurationPerDateResponse();
    }

}
