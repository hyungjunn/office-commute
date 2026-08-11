package com.company.officecommute.service.report;

import com.company.officecommute.domain.report.DispatchFailureReason;
import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.mail.ReportMailer;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.service.overtime.UnclosedCommute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OverTimeReportDispatchServiceTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");

    @Mock
    private ReportDispatchRepository reportDispatchRepository;

    @Mock
    private OverTimeReportService overTimeReportService;

    @Mock
    private OverTimeService overTimeService;

    @Mock
    private ReportMailer reportMailer;

    private OverTimeReportDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new OverTimeReportDispatchService(
                reportDispatchRepository,
                overTimeReportService,
                overTimeService,
                reportMailer,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("이미 발송된 달은 메일러를 한 번도 부르지 않는다 — 멱등의 본체")
    void alreadySent_doesNothing() {
        ReportDispatch sent = ReportDispatch.claim(JULY, NOW);
        sent.markSent(NOW);
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.of(sent));

        dispatchService.dispatch(JULY);

        then(reportMailer).shouldHaveNoInteractions();
        then(overTimeReportService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 실행이 리스를 잡고 있으면 중복으로 집계하지 않는다")
    void leaseHeldByAnotherRun_doesNothing() {
        ReportDispatch inProgress = ReportDispatch.claim(JULY, NOW);
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.of(inProgress));

        dispatchService.dispatch(JULY);

        then(reportMailer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("리스가 만료된 IN_PROGRESS는 회수해 다시 시도한다 — 그 달이 영원히 잠기면 안 된다")
    void expiredLease_isReclaimed() {
        ReportDispatch stale = ReportDispatch.claim(JULY, NOW.minusSeconds(3600));
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.of(stale));
        given(reportDispatchRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(overTimeReportService.generateReport(JULY)).willReturn(report(2, 0));

        dispatchService.dispatch(JULY);

        then(reportMailer).should().sendMonthlyReport(any(), any());
    }

    @Test
    @DisplayName("미마감이 있으면 대표에게 가지 않고 관리자 경고만 나가며 FAILED(UNCLOSED_COMMUTES)로 남는다")
    void unclosedCommutes_gatesCeoMail() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReport(JULY)).willReturn(report(2, 3));
        List<UnclosedCommute> unclosed = List.of(
                new UnclosedCommute("EMP001", "임형준", LocalDate.of(2026, 7, 31))
        );
        given(overTimeService.findUnclosedCommutes(JULY)).willReturn(unclosed);

        dispatchService.dispatch(JULY);

        then(reportMailer).should(never()).sendMonthlyReport(any(), any());
        then(reportMailer).should().sendUnclosedCommuteWarning(any(), any(), eq(unclosed));

        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.UNCLOSED_COMMUTES.name());
    }

    @Test
    @DisplayName("공휴일 API 이용 불가는 대표 미발송 + FAILED로 남고, 예외를 밖으로 던지지 않는다")
    void holidayDataUnavailable_recordedNotThrown() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReport(JULY))
                .willThrow(new HolidayDataUnavailableException("resultCode=22"));

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        then(reportMailer).should(never()).sendMonthlyReport(any(), any());
        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE.name());
    }

    @Test
    @DisplayName("SMTP 오류는 MAIL_SEND_FAILED로 분류되고 스케줄러 스레드를 죽이지 않는다")
    void mailFailure_classifiedAndSwallowed() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReport(JULY)).willReturn(report(2, 0));
        willThrow(new MailSendException("smtp down")).given(reportMailer).sendMonthlyReport(any(), any());

        assertThatCode(() -> dispatchService.dispatch(JULY)).doesNotThrowAnyException();

        ReportDispatch recorded = capturedSave();
        assertThat(recorded.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(recorded.getLastFailureReason())
                .startsWith(DispatchFailureReason.MAIL_SEND_FAILED.name());
    }

    @Test
    @DisplayName("성공하면 SENT + sentAt이 남고, 이어지는 두 번째 발송은 아무 일도 하지 않는다")
    void success_thenSecondDispatchIsNoop() {
        givenNoPriorDispatch();
        given(overTimeReportService.generateReport(JULY)).willReturn(report(2, 0));

        dispatchService.dispatch(JULY);

        ReportDispatch sent = capturedSave();
        assertThat(sent.isSent()).isTrue();
        assertThat(sent.getSentAt()).isEqualTo(NOW);

        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.of(sent));
        dispatchService.dispatch(JULY);

        then(reportMailer).should(times(1)).sendMonthlyReport(any(), any());
    }

    @Test
    @DisplayName("최종 점검에서 이미 발송된 달은 조용히 넘어간다")
    void alertIfNotSent_silentWhenSent() {
        ReportDispatch sent = ReportDispatch.claim(JULY, NOW);
        sent.markSent(NOW);
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.of(sent));

        dispatchService.alertIfNotSent(JULY);

        then(reportMailer).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("최종 점검에서 미발송이면 기록된 사유 분류로 관리자에게 알린다")
    void alertIfNotSent_reportsRecordedReason() {
        ReportDispatch failed = ReportDispatch.claim(JULY, NOW);
        failed.markFailed(DispatchFailureReason.UNCLOSED_COMMUTES.name() + ": 미마감 3건", NOW);
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.of(failed));

        dispatchService.alertIfNotSent(JULY);

        then(reportMailer).should().sendDispatchFailure(
                eq(JULY), eq(DispatchFailureReason.UNCLOSED_COMMUTES), any());
    }

    @Test
    @DisplayName("이력 자체가 없으면 스케줄러 미동작으로 보고 알린다 — 가장 조용한 실패다")
    void alertIfNotSent_noHistoryAtAll() {
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.empty());

        dispatchService.alertIfNotSent(JULY);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        then(reportMailer).should().sendDispatchFailure(
                eq(JULY), eq(DispatchFailureReason.UNEXPECTED), detail.capture());
        assertThat(detail.getValue()).contains("스케줄러");
    }

    private void givenNoPriorDispatch() {
        given(reportDispatchRepository.findByTargetYearMonth(JULY)).willReturn(Optional.empty());
        given(reportDispatchRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    /** 결과 기록은 markSent/markFailed 뒤의 save 로 남는다. */
    private ReportDispatch capturedSave() {
        ArgumentCaptor<ReportDispatch> captor = ArgumentCaptor.forClass(ReportDispatch.class);
        then(reportDispatchRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private OverTimeReport report(int rowCount, long unclosedCount) {
        List<OverTimeReportData> rows = java.util.stream.IntStream.range(0, rowCount)
                .mapToObj(i -> new OverTimeReportData("EMP00" + i, "직원" + i, "백엔드팀", 60, 0, 0, 22500))
                .toList();
        return new OverTimeReport(JULY, rows, unclosedCount);
    }
}
