package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OverTimeService {

    private static final String UNASSIGNED_TEAM_NAME = "미배정";

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final StandardWorkingTimeService standardWorkingTimeService;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            StandardWorkingTimeService standardWorkingTimeService
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.standardWorkingTimeService = standardWorkingTimeService;
    }

    /**
     * 대상 월에 퇴근이 찍히지 않은 기록 수. 0이 아니면 {@link #calculateOverTime} 결과가 과소 집계다.
     */
    public long countUnclosedCommutes(YearMonth yearMonth) {
        return commuteHistoryRepository.countByWorkDateBetweenAndWorkEndTimeIsNull(
                yearMonth.atDay(1), yearMonth.atEndOfMonth()
        );
    }

    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<TotalWorkingMinutes> totalWorkingMinutes = commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(startDate, endDate);
        Map<Long, TotalWorkingMinutes> totalWorkingMinutesByEmployeeId = totalWorkingMinutes.stream()
                .collect(Collectors.toMap(TotalWorkingMinutes::getEmployeeId, Function.identity(), (left, right) -> left));

        long numberOfStandardWorkingDays = standardWorkingTimeService.countNumberOfStandardWorkingDays(yearMonth);
        long standardWorkingMinutes = standardWorkingTimeService.calculateStandardWorkingMinutes(numberOfStandardWorkingDays);

        return employeeRepository.findAllWithTeam().stream()
                .map(employee -> {
                    TotalWorkingMinutes totalWorkingMinute = totalWorkingMinutesByEmployeeId.get(employee.getEmployeeId());
                    if (totalWorkingMinute == null) {
                        return new OverTimeCalculateResponse(
                                employee.getEmployeeId(),
                                employee.getEmployeeCode(),
                                employee.getName(),
                                employee.getTeamName() != null ? employee.getTeamName() : UNASSIGNED_TEAM_NAME,
                                0L
                        );
                    }
                    return new OverTimeCalculateResponse(
                            totalWorkingMinute.getEmployeeId(),
                            totalWorkingMinute.getEmployeeCode(),
                            totalWorkingMinute.getEmployeeName(),
                            totalWorkingMinute.getTeamName(),
                            totalWorkingMinute.calculateOverTime(standardWorkingMinutes)
                    );
                })
                .toList();
    }

}
