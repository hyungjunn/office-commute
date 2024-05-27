package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.commute.Detail;
import com.company.officecommute.domain.commute.Details;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class CommuteHistoryService {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;

    public CommuteHistoryService(CommuteHistoryRepository commuteHistoryRepository, EmployeeRepository employeeRepository) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void registerWorkStartTime(Long employeeId) {
        Employee employee = findEmployeeById(employeeId);

        // 직원의 마지막 출근 기록 조회
        commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(employee.getId())
                .ifPresent(lastCommute -> {
                    if (lastCommute.getWorkEndTime() == null) {
                        throw new IllegalArgumentException(String.format("직원 id(%d)는 퇴근하지 않고 다시 출근할 수 없습니다.", employee.getId()));
                    }
                });

        commuteHistoryRepository.save(new CommuteHistory(null, employee.getId(), ZonedDateTime.now(), null, 0));
    }

    @Transactional
    public void registerWorkEndTime(Long employeeId, ZonedDateTime workEndTime) {
        Employee employee = findEmployeeById(employeeId);

        CommuteHistory lastCommute = commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(employee.getId())
                .orElseThrow(() -> new IllegalArgumentException(String.format("존재하지 않은 근무 이력(%s)입니다.", employee.getId())));

        CommuteHistory commuteHistory = lastCommute.endWork(workEndTime);
        commuteHistoryRepository.save(commuteHistory);
    }

    @Transactional(readOnly = true)
    public WorkDurationPerDateResponse getWorkDurationPerDate(Long employeeId, YearMonth yearMonth) {
        Employee employee = findEmployeeById(employeeId);

        ZonedDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endOfMonth = yearMonth.atEndOfMonth().atStartOfDay(ZoneId.systemDefault());

        List<Detail> details = commuteHistoryRepository.findByEmployeeIdAndWorkStartTimeBetween(employee.getId(), startOfMonth, endOfMonth)
                .stream()
                .map(CommuteHistory::toDetail)
                .toList();

        long sumWorkingMinutes = new Details(details).sumWorkingMinutes();

        return new WorkDurationPerDateResponse(details, sumWorkingMinutes);
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(String.format("이 직원의 id(%d)는 존재하지 않습니다.", employeeId)));
    }

}
