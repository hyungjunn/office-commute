package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CommuteHistoryDomainService {

    private final CommuteHistoryRepository commuteHistoryRepository;

    public CommuteHistoryDomainService(CommuteHistoryRepository commuteHistoryRepository) {
        this.commuteHistoryRepository = commuteHistoryRepository;
    }

    public List<CommuteHistory> findCommuteHistoriesByEmployeeIdAndMonth(Long employeeId, YearMonth yearMonth) {
        return commuteHistoryRepository.findByEmployeeIdAndWorkStartTimeBetween(
                employeeId,
                convertStartOfMonthFrom(yearMonth),
                convertEndOfMonthFrom(yearMonth)
        );
    }

    private ZonedDateTime convertEndOfMonthFrom(YearMonth yearMonth) {
        return yearMonth.atEndOfMonth().atStartOfDay(ZoneId.systemDefault());
    }

    private ZonedDateTime convertStartOfMonthFrom(YearMonth yearMonth) {
        return yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault());
    }

    public void distinguishItIsPossibleToWork(Long employeeId) {
        findFirstByEmployeeIdOrderByWorkStartTimeDesc(employeeId)
                .ifPresent(lastCommute -> {
                    if (lastCommute.endTimeIsNull()) {
                        throw new IllegalArgumentException(String.format("직원 id(%d)는 퇴근하지 않고 다시 출근할 수 없습니다.", employeeId));
                    }
                });

    }

    private Optional<CommuteHistory> findFirstByEmployeeIdOrderByWorkStartTimeDesc(Long employeeId) {
        return commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(employeeId);
    }

    public CommuteHistory findFirstByDomainService(Long employeeId) {
        return commuteHistoryRepository.findFirstByEmployeeIdOrderByWorkStartTimeDesc(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(String.format("해당하는 직원(%s)의 출근 기록이 없습니다.", employeeId)));
    }

    public Map<Long, Long> findWorkingMinutesTimeByMonth(YearMonth yearMonth) {
        return commuteHistoryRepository.findWorkingMinutesTimeByEmployeeAndDateRange(
                convertStartOfMonthFrom(yearMonth),
                convertEndOfMonthFrom(yearMonth)
        );
    }
}
