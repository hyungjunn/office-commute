package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.commute.request.WorkStartTimeRequest;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Service;

@Service
public class CommuteHistoryService {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;

    public CommuteHistoryService(CommuteHistoryRepository commuteHistoryRepository, EmployeeRepository employeeRepository) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
    }

    public void registerWorkStartTime(WorkStartTimeRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new IllegalArgumentException(String.format("이 직원의 id(%d)는 존재하지 않습니다.", request.employeeId())));

        // 직원의 마지막 출근 기록 조회
        commuteHistoryRepository.findLatestWorkStartTimeByEmployeeId(employee.getId())
                        .ifPresent(lastCommute -> {
                            if (lastCommute.getWorkEndTime() == null) {
                                throw new IllegalArgumentException(String.format("직원 id(%d)는 퇴근하지 않고 다시 출근할 수 없습니다.", employee.getId()));
                            }
                        });

        commuteHistoryRepository.save(new CommuteHistory(null, employee.getId(), request.workStartTime(), null, 0));
    }
}
