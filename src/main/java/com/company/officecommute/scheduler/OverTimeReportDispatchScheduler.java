package com.company.officecommute.scheduler;

import com.company.officecommute.service.report.OverTimeReportDispatchService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 매월 1일 오전 전월 리포트를 대표에게 보낸다.
 * <p>
 * {@code @Profile("prod")}라 dev/test 에서는 빈 자체가 만들어지지 않는다 — 켜고 끄는
 * 플래그를 따로 둘 필요가 없고, 테스트 컨텍스트가 실수로 메일을 보낼 경로도 없다.
 * <p>
 * 발송 로직은 전부 {@link OverTimeReportDispatchService}에 있다. 이 클래스는 "언제"만 안다.
 */
@Component
@Profile("prod")
public class OverTimeReportDispatchScheduler {

    /**
     * 주입된 {@code Clock}은 {@code systemDefaultZone()}이라 서버 타임존에 따라 달이 밀릴 수 있다.
     * (예: 9/1 06:00 KST는 UTC로 8/31 21:00 — 서버 TZ가 UTC면 "전월"이 7월이 된다.)
     * cron 의 {@code zone}과 날짜 파생 zone을 <b>둘 다</b> KST로 못박아 서버 TZ와 무관하게
     * 같은 달을 가리키게 한다.
     */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OverTimeReportDispatchService dispatchService;
    private final Clock clock;

    public OverTimeReportDispatchScheduler(OverTimeReportDispatchService dispatchService, Clock clock) {
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    /**
     * 1~3일 × 하루 4회 = 최대 12회 시도. 첫 시도는 1일 06:00 KST.
     * <p>
     * 재시도가 공휴일 원장(저장 계층)을 대체한다 — API 다운은 리포트 실패가 아니라 지연이 되고,
     * 발송 이력의 유니크 제약 덕에 성공하는 순간 나머지 시도는 자동으로 무해해진다.
     */
    @Scheduled(cron = "0 0 6,10,14,18 1-3 * *", zone = "Asia/Seoul")
    public void dispatchPreviousMonth() {
        dispatchService.dispatch(previousMonth());
    }

    /**
     * 마지막 시도(3일 18:00) 2시간 뒤. 이 시점에 미발송이면 사람에게 드러내야 한다.
     */
    @Scheduled(cron = "0 0 20 3 * *", zone = "Asia/Seoul")
    public void alertIfPreviousMonthNotSent() {
        dispatchService.alertIfNotSent(previousMonth());
    }

    YearMonth previousMonth() {
        return YearMonth.from(LocalDate.now(clock.withZone(KST))).minusMonths(1);
    }
}
