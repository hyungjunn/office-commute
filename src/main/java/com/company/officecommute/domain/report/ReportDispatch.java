package com.company.officecommute.domain.report;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 한 대상 월의 발송 이력. 상태 전이를 엔티티가 소유해, "어디선가 status 만 바꿔치기"가
 * 생기지 않게 한다.
 * <p>
 * {@code UNIQUE(target_year_month)}가 중복 발송 방지의 유일한 하드 보증이다.
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_dispatch_year_month", columnNames = {"target_year_month"})
})
public class ReportDispatch {

    /** 실패 사유 컬럼 길이. 넘치면 저장 시점에 터지므로 자른다. */
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportDispatchId;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "target_year_month", nullable = false, length = 7)
    private YearMonth targetYearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchStatus status;

    /** 결과가 기록된 시도 횟수. 진행 중인 시도는 아직 세지 않는다. */
    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant lastAttemptedAt;

    private Instant sentAt;

    @Column(length = MAX_FAILURE_REASON_LENGTH)
    private String lastFailureReason;

    protected ReportDispatch() {
    }

    private ReportDispatch(YearMonth targetYearMonth, Instant now) {
        this.targetYearMonth = Objects.requireNonNull(targetYearMonth, "targetYearMonth는 null일 수 없습니다.");
        this.status = DispatchStatus.IN_PROGRESS;
        this.attemptCount = 0;
        this.lastAttemptedAt = Objects.requireNonNull(now, "now는 null일 수 없습니다.");
    }

    /** 이 달을 선점한다. 동시에 두 실행이 부르면 유니크 제약이 한쪽을 떨어뜨린다. */
    public static ReportDispatch claim(YearMonth targetYearMonth, Instant now) {
        return new ReportDispatch(targetYearMonth, now);
    }

    /**
     * 이미 있는 이력으로 새 시도를 시작한다(재시도 또는 리스 회수).
     * 실패 사유는 남겨 둔다 — 이번 시도가 또 실패할 때까지는 마지막으로 알려진 이유가 유효하다.
     */
    public void beginAttempt(Instant now) {
        this.status = DispatchStatus.IN_PROGRESS;
        this.lastAttemptedAt = now;
    }

    public void markSent(Instant now) {
        this.status = DispatchStatus.SENT;
        this.sentAt = now;
        this.lastAttemptedAt = now;
        this.attemptCount++;
        this.lastFailureReason = null;
    }

    public void markFailed(String reason, Instant now) {
        this.status = DispatchStatus.FAILED;
        this.lastAttemptedAt = now;
        this.attemptCount++;
        this.lastFailureReason = truncate(reason);
    }

    public boolean isSent() {
        return status == DispatchStatus.SENT;
    }

    /**
     * 다른 실행이 선점한 채 아직 살아 있는가.
     * 발송 도중 프로세스가 죽으면 {@code IN_PROGRESS}가 남는데, 리스가 없으면 그 달이
     * 영원히 잠긴다 — 리스 시간이 지난 선점은 회수한다.
     */
    public boolean isLeaseHeld(Instant now, Duration lease) {
        return status == DispatchStatus.IN_PROGRESS && lastAttemptedAt.plus(lease).isAfter(now);
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    public Long getReportDispatchId() {
        return reportDispatchId;
    }

    public YearMonth getTargetYearMonth() {
        return targetYearMonth;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }
}
