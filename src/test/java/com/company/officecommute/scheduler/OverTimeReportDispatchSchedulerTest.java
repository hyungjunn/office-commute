package com.company.officecommute.scheduler;

import com.company.officecommute.service.report.OverTimeReportDispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OverTimeReportDispatchSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String DISPATCH_CRON = "0 0 6,10,14,18 1-3 * *";
    private static final String ALERT_CRON = "0 0 20 3 * *";

    @Mock
    private OverTimeReportDispatchService dispatchService;

    @Test
    @DisplayName("발송 cron 의 첫 발화는 1일 06:00 이고 하루 4회다")
    void dispatchCron_firesFourTimesOnFirstDay() {
        CronExpression cron = CronExpression.parse(DISPATCH_CRON);

        LocalDateTime first = cron.next(LocalDateTime.of(2026, 8, 31, 23, 59));

        assertThat(first).isEqualTo(LocalDateTime.of(2026, 9, 1, 6, 0));
        assertThat(cron.next(first)).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 9, 1, 10, 0))).isEqualTo(LocalDateTime.of(2026, 9, 1, 14, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 9, 1, 14, 0))).isEqualTo(LocalDateTime.of(2026, 9, 1, 18, 0));
    }

    @Test
    @DisplayName("발송 cron 은 3일 18:00 이 마지막이고 4일에는 발화하지 않는다 — 재시도 창은 1~3일")
    void dispatchCron_stopsAfterThirdDay() {
        CronExpression cron = CronExpression.parse(DISPATCH_CRON);

        LocalDateTime afterLastAttempt = cron.next(LocalDateTime.of(2026, 9, 3, 18, 0));

        // 다음 발화는 4일이 아니라 다음 달 1일이다
        assertThat(afterLastAttempt).isEqualTo(LocalDateTime.of(2026, 10, 1, 6, 0));
    }

    @Test
    @DisplayName("최종 실패 알림 cron 은 마지막 시도 2시간 뒤인 3일 20:00 에 한 번 발화한다")
    void alertCron_firesOnceOnThirdDayEvening() {
        CronExpression cron = CronExpression.parse(ALERT_CRON);

        LocalDateTime fire = cron.next(LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(fire).isEqualTo(LocalDateTime.of(2026, 9, 3, 20, 0));
        assertThat(cron.next(fire)).isEqualTo(LocalDateTime.of(2026, 10, 3, 20, 0));
    }

    @Test
    @DisplayName("서버 타임존이 UTC여도 1일 06:00 KST 발화는 전월을 가리킨다")
    void previousMonth_derivedInKstNotServerZone() {
        // 2026-09-01 06:00 KST == 2026-08-31 21:00 UTC. 서버 TZ 기준으로 파생하면 7월이 되어 한 달 밀린다.
        OverTimeReportDispatchScheduler scheduler = schedulerAt(
                ZonedDateTime.of(2026, 9, 1, 6, 0, 0, 0, KST).toInstant(), ZoneOffset.UTC);

        scheduler.dispatchPreviousMonth();

        then(dispatchService).should().dispatch(YearMonth.of(2026, 8));
    }

    @Test
    @DisplayName("1월 1일에는 전년 12월을 대상으로 삼는다")
    void previousMonth_crossesYearBoundary() {
        OverTimeReportDispatchScheduler scheduler = schedulerAt(
                ZonedDateTime.of(2026, 1, 1, 6, 0, 0, 0, KST).toInstant(), KST);

        scheduler.dispatchPreviousMonth();

        then(dispatchService).should().dispatch(YearMonth.of(2025, 12));
    }

    @Test
    @DisplayName("최종 실패 점검도 같은 대상 월(전월)을 본다 — 3일에 불러도 대상은 바뀌지 않는다")
    void alert_targetsSamePreviousMonth() {
        OverTimeReportDispatchScheduler scheduler = schedulerAt(
                ZonedDateTime.of(2026, 9, 3, 20, 0, 0, 0, KST).toInstant(), ZoneOffset.UTC);

        scheduler.alertIfPreviousMonthNotSent();

        then(dispatchService).should().alertIfNotSent(YearMonth.of(2026, 8));
    }

    private OverTimeReportDispatchScheduler schedulerAt(Instant instant, ZoneId serverZone) {
        return new OverTimeReportDispatchScheduler(dispatchService, Clock.fixed(instant, serverZone));
    }
}
