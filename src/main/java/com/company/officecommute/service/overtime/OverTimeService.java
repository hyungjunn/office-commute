package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.web.ApiConvertor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class OverTimeService {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final ApiConvertor apiConvertor;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            ApiConvertor apiConvertor
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.apiConvertor = apiConvertor;
    }

    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        ZonedDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZonedDateTime.now().getZone());
        List<TotalWorkingMinutes> totalWorkingMinutes = commuteHistoryRepository.findWithEmployeeIdByDateRange(startOfMonth, endOfMonth);
        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);
        long standardWorkingMinutes = apiConvertor.calculateStandardWorkingMinutes(numberOfStandardWorkingDays);
        return totalWorkingMinutes.stream()
                .map(totalWorkingMinute -> {
                    long overTime = totalWorkingMinute.calculateOverTime(standardWorkingMinutes);
                    return new OverTimeCalculateResponse(totalWorkingMinute.getEmployeeId(), totalWorkingMinute.getEmployeeName(), overTime);
                })
                .toList();
    }

}
