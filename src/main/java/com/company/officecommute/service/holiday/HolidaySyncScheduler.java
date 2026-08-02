package com.company.officecommute.service.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.List;

/**
 * 매일 새벽 공휴일 원장을 갱신한다.
 * <p>
 * 연 1회 적재는 부트스트랩이지 전략이 아니다 — 한국 공휴일은 연중에 늘어난다(임시공휴일은
 * 국무회의에서 몇 주 전 지정, 선거일은 사후 지정). 연초에 한 번 적재하고 끝내면 원장은 조용히
 * 낡는데 마커는 "적재됨"이라 계산이 그대로 통과한다. 매일 도는 것이 그 창을 닫는다.
 * <p>
 * 스케줄러는 얇은 어댑터다. 무엇을 적재할지는 {@link HolidaySyncService#syncUpcomingYears()}가 정한다.
 */
@Component
@ConditionalOnProperty(name = "holiday.sync.scheduled.enabled", havingValue = "true", matchIfMissing = true)
public class HolidaySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncScheduler.class);

    private final HolidaySyncService holidaySyncService;

    public HolidaySyncScheduler(HolidaySyncService holidaySyncService) {
        this.holidaySyncService = holidaySyncService;
    }

    @Scheduled(cron = "${holiday.sync.cron:0 10 3 * * *}", zone = "Asia/Seoul")
    public void syncHolidays() {
        List<Year> syncedYears = holidaySyncService.syncUpcomingYears();
        log.info("공휴일 정기 동기화 종료: 적재된 연도={}", syncedYears);
    }
}
