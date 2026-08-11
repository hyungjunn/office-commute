package com.company.officecommute.service.report;

import com.company.officecommute.domain.report.DispatchFailureReason;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.mail.ReportMailException;
import com.company.officecommute.mail.ReportMailer;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.service.overtime.UnclosedCommute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 초과근무 리포트 발송의 <b>유일한 진입점</b>. 매월 배치도 관리자의 수동 재실행도 이것을 부른다.
 * <p>
 * 멱등성의 근거는 {@code report_dispatch}의 {@code UNIQUE(target_year_month)}다 —
 * 이미 {@code SENT}인 달은 몇 번을 불러도 아무 일도 일어나지 않으므로 강제 발송 플래그를 두지 않는다.
 * <p>
 * 이 클래스는 예외를 밖으로 던지지 않는다. 스케줄러 스레드에서 예외가 올라가면 그 뒤의
 * 재시도까지 함께 사라진다 — 모든 실패는 이력에 기록되고 분류된다.
 */
@Service
public class OverTimeReportDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OverTimeReportDispatchService.class);

    /**
     * 선점 리스. 발송 도중 프로세스가 죽으면 {@code IN_PROGRESS}가 남는데, 회수 없이는
     * 그 달이 영원히 잠긴다. 한 번의 발송(집계 + 엑셀 + SMTP)이 넉넉히 끝나는 시간으로 잡는다.
     */
    private static final Duration LEASE = Duration.ofMinutes(30);

    private final ReportDispatchRepository reportDispatchRepository;
    private final OverTimeReportService overTimeReportService;
    private final OverTimeService overTimeService;
    private final ReportMailer reportMailer;
    private final Clock clock;

    public OverTimeReportDispatchService(
            ReportDispatchRepository reportDispatchRepository,
            OverTimeReportService overTimeReportService,
            OverTimeService overTimeService,
            ReportMailer reportMailer,
            Clock clock
    ) {
        this.reportDispatchRepository = reportDispatchRepository;
        this.overTimeReportService = overTimeReportService;
        this.overTimeService = overTimeService;
        this.reportMailer = reportMailer;
        this.clock = clock;
    }

    public void dispatch(YearMonth target) {
        ReportDispatch dispatch = claim(target);
        if (dispatch == null) {
            return;
        }

        log.info("초과근무 리포트 발송 시도 시작 — 대상 월 {}, 시도 {}회차", target, dispatch.getAttemptCount() + 1);
        try {
            runDispatch(target, dispatch);
        } catch (HolidayDataUnavailableException e) {
            // 공휴일 fail-closed. 사람이 할 조치가 없고 재시도가 흡수한다.
            recordFailure(dispatch, DispatchFailureReason.HOLIDAY_DATA_UNAVAILABLE, e.getMessage());
        } catch (ReportMailException | MailException e) {
            recordFailure(dispatch, DispatchFailureReason.MAIL_SEND_FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("초과근무 리포트 발송 중 예상하지 못한 오류 — 대상 월 {}", target, e);
            recordFailure(dispatch, DispatchFailureReason.UNEXPECTED, e.toString());
        }
    }

    /**
     * 재시도 창을 다 쓰고도 발송되지 않은 달을 관리자에게 드러낸다.
     * "메일이 안 온 것"과 "발송이 실패한 것"이 구분되지 않으면 그것이 조용한 실패다.
     * <p>
     * 시도 로직에 "이번이 마지막인가" 조건을 섞지 않기 위해 별도 진입점으로 둔다.
     */
    public void alertIfNotSent(YearMonth target) {
        Optional<ReportDispatch> found = reportDispatchRepository.findByTargetYearMonth(target);
        if (found.isPresent() && found.get().isSent()) {
            return;
        }

        String detail = found
                .map(d -> "%d회 시도, 마지막 사유: %s".formatted(d.getAttemptCount(), d.getLastFailureReason()))
                .orElse("발송이 한 번도 시도되지 않았습니다 — 스케줄러 미동작을 의심해야 합니다.");
        log.error("초과근무 리포트 최종 미발송 — 대상 월 {} ({})", target, detail);

        try {
            reportMailer.sendDispatchFailure(target, found.map(this::recordedReason).orElse(DispatchFailureReason.UNEXPECTED), detail);
        } catch (ReportMailException | MailException e) {
            // 알림 경로 자체가 죽은 경우다. 메일로 알릴 방법이 없으니 로그가 마지막 방어선이다.
            log.error("최종 미발송 알림 메일마저 실패했다 — 대상 월 {}", target, e);
        }
    }

    /** {@code markFailed}가 남긴 "REASON: 상세" 문자열에서 분류를 되읽는다. */
    private DispatchFailureReason recordedReason(ReportDispatch dispatch) {
        String recorded = dispatch.getLastFailureReason();
        if (recorded == null) {
            return DispatchFailureReason.UNEXPECTED;
        }
        for (DispatchFailureReason reason : DispatchFailureReason.values()) {
            if (recorded.startsWith(reason.name())) {
                return reason;
            }
        }
        return DispatchFailureReason.UNEXPECTED;
    }

    /**
     * 이 달을 이 실행이 가져갈 수 있으면 선점한 이력을, 아니면 {@code null}을 준다.
     * <p>
     * 선점 경합은 유니크 제약이 판정한다 — 애플리케이션 락을 따로 두지 않는다.
     */
    private ReportDispatch claim(YearMonth target) {
        Instant now = clock.instant();
        Optional<ReportDispatch> found = reportDispatchRepository.findByTargetYearMonth(target);

        if (found.isEmpty()) {
            try {
                return reportDispatchRepository.saveAndFlush(ReportDispatch.claim(target, now));
            } catch (DataIntegrityViolationException e) {
                log.info("다른 실행이 이미 선점했다 — 대상 월 {}", target);
                return null;
            }
        }

        ReportDispatch dispatch = found.get();
        if (dispatch.isSent()) {
            log.info("이미 발송된 달이라 아무것도 하지 않는다 — 대상 월 {}, 발송 시각 {}", target, dispatch.getSentAt());
            return null;
        }
        if (dispatch.isLeaseHeld(now, LEASE)) {
            log.info("다른 실행이 진행 중이다 — 대상 월 {}, 마지막 시도 {}", target, dispatch.getLastAttemptedAt());
            return null;
        }

        dispatch.beginAttempt(now);
        return reportDispatchRepository.save(dispatch);
    }

    /**
     * 집계·엑셀·발송은 전부 DB 트랜잭션 <b>밖</b>이다. {@code generateReport}가 공휴일 API를
     * 라이브 호출하므로, 트랜잭션 안에 두면 외부 API 응답 시간만큼 커넥션을 붙잡는다.
     */
    private void runDispatch(YearMonth target, ReportDispatch dispatch) throws Exception {
        OverTimeReport report = overTimeReportService.generateReport(target);
        byte[] excel = writeExcel(report);

        if (report.hasUnclosedCommutes()) {
            // 미마감은 그 직원의 초과근무를 과소 집계한다 = 조용히 틀린 급여 근거.
            // 엑셀 첫 행 경고만으로는 "대표가 경고를 읽었을 것"에 기대게 되므로 발송 자체를 막는다.
            List<UnclosedCommute> unclosed = overTimeService.findUnclosedCommutes(target);
            reportMailer.sendUnclosedCommuteWarning(report, excel, unclosed);
            recordFailure(
                    dispatch,
                    DispatchFailureReason.UNCLOSED_COMMUTES,
                    "미마감 %d건 — 마감되면 다음 재시도에서 자동 발송된다".formatted(report.unclosedCommuteCount())
            );
            return;
        }

        reportMailer.sendMonthlyReport(report, excel);
        dispatch.markSent(clock.instant());
        reportDispatchRepository.save(dispatch);
        log.info("초과근무 리포트 발송 완료 — 대상 월 {}, 대상자 {}명", target, report.rows().size());
    }

    /**
     * 엑셀을 <b>다 만든 뒤</b> 첨부한다. 쓰는 도중 실패하면 메일은 시작조차 하지 않으므로
     * 절반짜리 파일이 대표에게 갈 수 없다.
     */
    private byte[] writeExcel(OverTimeReport report) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        overTimeReportService.writeExcelReport(report, buffer);
        return buffer.toByteArray();
    }

    private void recordFailure(ReportDispatch dispatch, DispatchFailureReason reason, String detail) {
        log.warn("초과근무 리포트 발송 실패 — 대상 월 {}, 사유 {}, 상세 {}",
                dispatch.getTargetYearMonth(), reason, detail);
        dispatch.markFailed(reason.name() + ": " + detail, clock.instant());
        reportDispatchRepository.save(dispatch);
    }
}
