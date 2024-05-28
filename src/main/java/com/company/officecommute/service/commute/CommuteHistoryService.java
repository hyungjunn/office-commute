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
        commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(employee.getEmployeeId())
                .ifPresent(lastCommute -> {
                    if (lastCommute.endTimeIsNull()) {
                        throw new IllegalArgumentException(String.format("직원 id(%d)는 퇴근하지 않고 다시 출근할 수 없습니다.", employee.getEmployeeId()));
                    }
                });

        commuteHistoryRepository.save(new CommuteHistory(null, employee.getEmployeeId(), ZonedDateTime.now(), null, 0));
    }

    @Transactional
    public void registerWorkEndTime(Long employeeId, ZonedDateTime workEndTime) {
        Employee employee = findEmployeeById(employeeId);

        CommuteHistory lastCommute = commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(employee.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException(String.format("존재하지 않은 근무 이력(%s)입니다.", employee.getEmployeeId())));

        CommuteHistory commuteHistory = lastCommute.endWork(workEndTime);
        commuteHistoryRepository.save(commuteHistory);
    }

    private Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(String.format("이 직원의 id(%d)는 존재하지 않습니다.", employeeId)));
    }

    @Transactional(readOnly = true)
    public WorkDurationPerDateResponse getWorkDurationPerDate(Long employeeId, YearMonth yearMonth) {
        Employee employee = findEmployeeById(employeeId);

        // todo: 순수 자바 라이브러리 계산이 서비스 계층에 들어가는게 바람직한건가? 대안은?
        ZonedDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endOfMonth = yearMonth.atEndOfMonth().atStartOfDay(ZoneId.systemDefault());

        List<Detail> details = commuteHistoryRepository.findByEmployeeIdAndWorkStartTimeBetween(employee.getEmployeeId(), startOfMonth, endOfMonth)
                .stream()
                .map(CommuteHistory::toDetail)
                .toList();

        long sumWorkingMinutes = new Details(details).sumWorkingMinutes();

        return new WorkDurationPerDateResponse(details, sumWorkingMinutes);
    }

}
