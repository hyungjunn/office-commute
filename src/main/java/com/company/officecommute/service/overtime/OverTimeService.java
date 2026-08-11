package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
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
    private final OverTimeSnapshotReader overTimeSnapshotReader;
    private final HolidayApiClient holidayApiClient;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            OverTimeSnapshotReader overTimeSnapshotReader,
            HolidayApiClient holidayApiClient
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.overTimeSnapshotReader = overTimeSnapshotReader;
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

    /**
     * {@link #countUnclosedCommutes}와 <b>정확히 같은 범위</b>의 미마감 기록 목록.
     * 관리자에게 교정을 요청하려면 건수가 아니라 "무엇을"이 필요하다.
     * 두 메서드가 범위를 각자 계산하면 "건수는 3건인데 목록은 2건"이 되므로 한 곳에서만 만든다.
     */
    public List<UnclosedCommute> findUnclosedCommutes(YearMonth yearMonth) {
        return commuteHistoryRepository.findUnclosedByWorkDateBetween(
                MonthlyOverTimeCalculator.requiredRangeStart(yearMonth), yearMonth.atEndOfMonth()
        );
    }

    /**
     * 공휴일 조회는 외부 API 라이브 호출이라 <b>읽기 트랜잭션 밖</b>에 둔다.
     * DB 스냅샷 안에 넣으면 외부 API 응답 시간만큼 커넥션을 붙잡고, 그 API 가 느려지는 날
     * 커넥션 풀이 먼저 마른다. 두 DB 조회의 일관성은
     * {@link OverTimeSnapshotReader}가 하나의 읽기 트랜잭션으로 보장한다.
     */
    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        Set<LocalDate> holidays = findHolidays(yearMonth);
        OverTimeSnapshot snapshot = overTimeSnapshotReader.read(yearMonth);

        Map<Long, Map<LocalDate, Long>> workingMinutesByEmployee = snapshot.dailyWorkingMinutes().stream()
                .collect(Collectors.groupingBy(
                        DailyWorkingMinutes::employeeId,
                        Collectors.toMap(DailyWorkingMinutes::workDate, DailyWorkingMinutes::workingMinutes)
                ));

        return snapshot.employees().stream()
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

    /**
     * 대상 월 1일이 속한 주가 전월에 걸치면 전월 공휴일도 필요하다 — 그 주의 휴일근로 분류에 쓰인다.
     */
    private Set<LocalDate> findHolidays(YearMonth yearMonth) {
        Set<LocalDate> holidays = new HashSet<>(holidayApiClient.getHolidays(yearMonth));
        YearMonth firstWeekMonth = YearMonth.from(MonthlyOverTimeCalculator.requiredRangeStart(yearMonth));
        if (!firstWeekMonth.equals(yearMonth)) {
            holidays.addAll(holidayApiClient.getHolidays(firstWeekMonth));
        }
        return holidays;
    }
}
