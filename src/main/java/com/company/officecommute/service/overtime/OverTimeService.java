package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.commute.CommuteHistoryDomainService;
import com.company.officecommute.web.ApiConvertor;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
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

    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) throws MalformedURLException, URISyntaxException {
        // 각 직원의 월간 근무 시간을 조회
        List<TotalWorkingMinutes> totalWorkingMinutes = commuteHistoryDomainService.findWorkingMinutesTimeByMonth(yearMonth);
        // 1. 공공데이터포털 사이트에서 주말과 법정 공휴일 데이터를 가지고 온다
        // 2. 그 달의 총 일 수 에서 1번(주말+공휴일)을 뺀다
        // 이 때, 공휴일이면서 주말인 경우는 겹치기 때문에 그 일수만큼 더해준다
        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);
        // 3. 2번에 곱하기 8을 한다
        long standardWorkingMinutes = apiConvertor.calculateStandardWorkingMinutes(numberOfStandardWorkingDays);
        return totalWorkingMinutes.stream()
                .map(totalWorkingMinute -> {
                    long overTime = totalWorkingMinute.calculateOverTime(standardWorkingMinutes);
                    return new OverTimeCalculateResponse(totalWorkingMinute.getEmployeeId(), totalWorkingMinute.getEmployeeName(), overTime);
                })
                .toList();
    }

}
