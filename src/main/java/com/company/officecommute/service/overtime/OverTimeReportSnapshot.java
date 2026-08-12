package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeReport;

import java.util.List;
import java.util.Objects;

/**
 * 한 번의 조회에서 만든 리포트와 미마감 상세 목록. 리포트의 건수와 경고 메일의 목록이
 * 서로 다른 시점의 조회 결과로 갈라지지 않게 함께 전달한다.
 */
public record OverTimeReportSnapshot(
        OverTimeReport report,
        List<UnclosedCommute> unclosedCommutes
) {
    public OverTimeReportSnapshot {
        Objects.requireNonNull(report, "report는 null일 수 없습니다.");
        unclosedCommutes = List.copyOf(unclosedCommutes);
        if (report.unclosedCommuteCount() != unclosedCommutes.size()) {
            throw new IllegalArgumentException("리포트의 미마감 건수와 상세 목록 크기가 일치해야 합니다.");
        }
    }
}
