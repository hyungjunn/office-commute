package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.web.HolidayApiClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OverTimeService {

    private static final String UNASSIGNED_TEAM_NAME = "미배정";

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayApiClient holidayApiClient;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            HolidayApiClient holidayApiClient
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.holidayApiClient = holidayApiClient;
    }

    /**
     * {@link #calculateOverTime}이 소비하는 범위(스필오버 주 월요일 ~ 월 말일)에서 퇴근이 찍히지
     * 않은 기록 수. 0이면 미마감으로 인한 과소 집계가 없음이 보장되고, 0이 아니면 과소 집계일 수 있다.
     * 전월 말 스필오버 일자도 첫 주의 40시간 판정에 0분으로 들어가므로 검사 범위에 포함해야 한다.
     */
    public long countUnclosedCommutes(YearMonth yearMonth) {
        return commuteHistoryRepository.countByWorkDateBetweenAndWorkEndTimeIsNull(
                MonthlyOverTimeCalculator.requiredRangeStart(yearMonth), yearMonth.atEndOfMonth()
        );
    }

    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        // 대상 월 1일이 속한 주(월~일)의 월요일부터 조회 — 그 주의 40시간 판정에 전월 말 기록이 필요하다
        LocalDate rangeStart = MonthlyOverTimeCalculator.requiredRangeStart(yearMonth);
        LocalDate rangeEnd = yearMonth.atEndOfMonth();

        Set<LocalDate> holidays = findHolidays(yearMonth, rangeStart);
        Map<Long, Map<LocalDate, Long>> workingMinutesByEmployee =
                commuteHistoryRepository.findDailyWorkingMinutesByWorkDateBetween(rangeStart, rangeEnd).stream()
                        .collect(Collectors.groupingBy(
                                DailyWorkingMinutes::employeeId,
                                Collectors.toMap(DailyWorkingMinutes::workDate, DailyWorkingMinutes::workingMinutes)
                        ));

        return employeeRepository.findAllWithTeam().stream()
                .map(employee -> {
                    MonthlyOverTime overTime = MonthlyOverTimeCalculator.calculate(
                            yearMonth,
                            workingMinutesByEmployee.getOrDefault(employee.getEmployeeId(), Map.of()),
                            holidays
                    );
                    return new OverTimeCalculateResponse(
                            employee.getEmployeeId(),
                            employee.getEmployeeCode(),
                            employee.getName(),
                            employee.getTeamName() != null ? employee.getTeamName() : UNASSIGNED_TEAM_NAME,
                            overTime.overTimeMinutes(),
                            overTime.holidayWithin8HoursMinutes(),
                            overTime.holidayExceeding8HoursMinutes()
                    );
                })
                .toList();
    }

    private Set<LocalDate> findHolidays(YearMonth yearMonth, LocalDate rangeStart) {
        Set<LocalDate> holidays = new HashSet<>(holidayApiClient.getHolidays(yearMonth));
        YearMonth firstWeekMonth = YearMonth.from(rangeStart);
        if (!firstWeekMonth.equals(yearMonth)) {
            holidays.addAll(holidayApiClient.getHolidays(firstWeekMonth));
        }
        return holidays;
    }
}
