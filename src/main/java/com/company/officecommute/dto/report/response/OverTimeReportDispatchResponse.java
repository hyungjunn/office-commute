package com.company.officecommute.dto.report.response;

import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;

import java.time.Instant;
import java.time.YearMonth;

/**
 * 수동 재실행의 응답. 관리자가 미마감을 고친 뒤 다음 재시도를 기다리지 않고
 * 결과를 즉시 확인할 수 있어야 하므로, 현재 발송 상태를 그대로 돌려준다.
 */
public record OverTimeReportDispatchResponse(
        YearMonth yearMonth,
        DispatchStatus status,
        int attemptCount,
        Instant sentAt,
        String lastFailureReason
) {
    public static OverTimeReportDispatchResponse from(ReportDispatch dispatch) {
        return new OverTimeReportDispatchResponse(
                dispatch.getTargetYearMonth(),
                dispatch.getStatus(),
                dispatch.getAttemptCount(),
                dispatch.getSentAt(),
                dispatch.getLastFailureReason()
        );
    }
}
