package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.commute.CommuteHistoryDomainService;
import com.company.officecommute.web.ApiConvertor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class OverTimeService {

    private final CommuteHistoryDomainService commuteHistoryDomainService;
    private final ApiConvertor apiConvertor;

    public OverTimeService(
            CommuteHistoryDomainService commuteHistoryDomainService,
            ApiConvertor apiConvertor
    ) {
        this.commuteHistoryDomainService = commuteHistoryDomainService;
        this.apiConvertor = apiConvertor;
    }

    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        List<TotalWorkingMinutes> totalWorkingMinutes = commuteHistoryDomainService.findWorkingMinutesTimeByMonth(yearMonth);
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
