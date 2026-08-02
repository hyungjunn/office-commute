package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.working_time.StandardWorkingTime;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.holiday.HolidayLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final HolidayLedgerService holidayLedgerService;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            HolidayLedgerService holidayLedgerService
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.holidayLedgerService = holidayLedgerService;
    }

    /**
     * 대상 월에 퇴근이 찍히지 않은 기록 수. 0이 아니면 {@link #calculateOverTime} 결과가 과소 집계다.
     */
    public long countUnclosedCommutes(YearMonth yearMonth) {
        return commuteHistoryRepository.countByWorkDateBetweenAndWorkEndTimeIsNull(
                yearMonth.atDay(1), yearMonth.atEndOfMonth()
        );
    }

    /**
     * 급여 근거가 되는 집계다. 외부 API를 타지 않고 공휴일 원장만 읽으므로 결정론적이고,
     * 세 쿼리가 한 스냅샷을 보도록 읽기 트랜잭션으로 묶는다.
     * <p>
     * 대상 월이 원장에 적재되지 않았으면 값 대신
     * {@link com.company.officecommute.domain.holiday.HolidayMonthNotLoadedException}으로 계산을 거부한다 —
     * 빈 원장을 "공휴일 0개"로 읽으면 기준선이 과대 계산되어 전 직원 초과근무가 과소 집계된다.
     */
    @Transactional(readOnly = true)
    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<TotalWorkingMinutes> totalWorkingMinutes = commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(startDate, endDate);
        Map<Long, TotalWorkingMinutes> totalWorkingMinutesByEmployeeId = totalWorkingMinutes.stream()
                .collect(Collectors.toMap(TotalWorkingMinutes::getEmployeeId, Function.identity(), (left, right) -> left));

        StandardWorkingTime standardWorkingTime =
                StandardWorkingTime.of(yearMonth, holidayLedgerService.getHolidayDates(yearMonth));
        long standardWorkingMinutes = standardWorkingTime.workingMinutes();

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
