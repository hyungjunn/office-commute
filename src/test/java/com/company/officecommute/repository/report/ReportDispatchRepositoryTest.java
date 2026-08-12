package com.company.officecommute.repository.report;

import com.company.officecommute.domain.report.ReportDispatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ReportDispatchRepositoryTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");

    @Autowired
    private ReportDispatchRepository reportDispatchRepository;

    @Test
    @DisplayName("같은 대상 월로 두 번 선점하면 두 번째가 제약 위반으로 실패한다 — 중복 발송 방지의 하드 보증")
    void duplicateYearMonth_violatesUniqueConstraint() {
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY, NOW));

        assertThatThrownBy(() -> reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 대상 월은 나란히 존재한다")
    void differentYearMonth_coexists() {
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY, NOW));
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY.minusMonths(1), NOW));

        assertThat(reportDispatchRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("YearMonth는 'yyyy-MM' 문자열로 저장돼 그대로 되읽힌다")
    void yearMonthRoundTrips() {
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY, NOW));
        reportDispatchRepository.flush();

        assertThat(reportDispatchRepository.findByTargetYearMonth(JULY))
                .isPresent()
                .get()
                .extracting(ReportDispatch::getTargetYearMonth)
                .isEqualTo(JULY);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 FAILED 이력을 동시에 읽어 재선점해도 한 실행만 낙관적 락을 통과한다")
    void concurrentRetry_onlyOneClaimSucceeds() {
        ReportDispatch failed = ReportDispatch.claim(JULY, NOW.minusSeconds(60));
        failed.markFailed("temporary failure", NOW.minusSeconds(30));
        reportDispatchRepository.saveAndFlush(failed);

        try {
            ReportDispatch first = reportDispatchRepository.findByTargetYearMonth(JULY).orElseThrow();
            ReportDispatch second = reportDispatchRepository.findByTargetYearMonth(JULY).orElseThrow();

            first.beginAttempt(NOW);
            second.beginAttempt(NOW.plusSeconds(1));

            reportDispatchRepository.saveAndFlush(first);

            assertThatThrownBy(() -> reportDispatchRepository.saveAndFlush(second))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            reportDispatchRepository.deleteAll();
        }
    }
}
