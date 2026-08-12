package com.company.officecommute.domain.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDispatchTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(30);

    @Test
    @DisplayName("선점 직후는 IN_PROGRESS이고 아직 시도 횟수가 0이다")
    void claim_startsInProgress() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.IN_PROGRESS);
        assertThat(dispatch.getAttemptCount()).isZero();
        assertThat(dispatch.isSent()).isFalse();
    }

    @Test
    @DisplayName("발송 성공은 SENT + sentAt을 남기고 이전 실패 사유를 지운다")
    void markSent_clearsFailureReason() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);
        dispatch.markFailed("HOLIDAY_DATA_UNAVAILABLE", NOW);

        dispatch.markSent(NOW.plus(Duration.ofHours(4)));

        assertThat(dispatch.isSent()).isTrue();
        assertThat(dispatch.getSentAt()).isEqualTo(NOW.plus(Duration.ofHours(4)));
        assertThat(dispatch.getLastFailureReason()).isNull();
        assertThat(dispatch.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("발송 확정은 SENT 저장 전이어도 자동 재시도 대상이 아니다")
    void commitDelivery_isFinalizedBeforeSent() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);

        dispatch.commitDelivery(NOW.plusSeconds(1));

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.DELIVERY_COMMITTED);
        assertThat(dispatch.isSent()).isFalse();
        assertThat(dispatch.isDeliveryFinalized()).isTrue();
        assertThat(dispatch.isLeaseHeld(NOW.plus(Duration.ofHours(1)), LEASE)).isFalse();
    }

    @Test
    @DisplayName("실패는 시도 횟수를 올리고 사유를 남긴다")
    void markFailed_recordsReason() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);

        dispatch.markFailed("UNCLOSED_COMMUTES: 3건", NOW);

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(dispatch.getAttemptCount()).isEqualTo(1);
        assertThat(dispatch.getLastFailureReason()).isEqualTo("UNCLOSED_COMMUTES: 3건");
    }

    @Test
    @DisplayName("실패 사유가 컬럼 길이를 넘으면 잘라 저장한다 — 기록하려다 저장이 터지면 본말전도")
    void markFailed_truncatesLongReason() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);

        dispatch.markFailed("x".repeat(1500), NOW);

        assertThat(dispatch.getLastFailureReason()).hasSize(1000);
    }

    @Test
    @DisplayName("리스 시간 안의 IN_PROGRESS는 다른 실행이 잡고 있는 것으로 본다")
    void isLeaseHeld_withinLease() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);

        assertThat(dispatch.isLeaseHeld(NOW.plus(Duration.ofMinutes(29)), LEASE)).isTrue();
    }

    @Test
    @DisplayName("리스 시간이 지난 IN_PROGRESS는 회수 대상 — 그 달이 영원히 잠기면 안 된다")
    void isLeaseHeld_expired() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);

        assertThat(dispatch.isLeaseHeld(NOW.plus(Duration.ofMinutes(31)), LEASE)).isFalse();
    }

    @Test
    @DisplayName("FAILED는 진행 중이 아니므로 리스와 무관하게 다음 시도가 집어간다")
    void isLeaseHeld_failedIsNotHeld() {
        ReportDispatch dispatch = ReportDispatch.claim(JULY, NOW);
        dispatch.markFailed("MAIL_SEND_FAILED", NOW);

        assertThat(dispatch.isLeaseHeld(NOW, LEASE)).isFalse();
    }
}
