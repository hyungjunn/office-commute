package com.company.officecommute.scheduler;

import com.company.officecommute.web.ApiConvertor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class HolidayScheduler {

    private static final Logger log = LoggerFactory.getLogger(HolidayScheduler.class);

    private final ApiConvertor apiConvertor;

    public HolidayScheduler(ApiConvertor apiConvertor) {
        this.apiConvertor = apiConvertor;
    }

    /**
     * 매주 월요일 새벽 3시에 현재/다음 달 공휴일 갱신
     */
    @Scheduled(cron = "0 0 3 * * MON")
    public void weeklyHolidayRefresh() {
        YearMonth current = YearMonth.now();
        YearMonth next = current.plusMonths(1);

        log.info("주간 공휴일 갱신 시작: {}, {}", current, next);

        apiConvertor.refreshHolidays(current);
        apiConvertor.refreshHolidays(next);

        log.info("주간 공휴일 갱신 완료");
    }
}
