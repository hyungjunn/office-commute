package com.company.officecommute.dto.overtime.response;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * 한 달치 초과근무 리포트. 집계 행과, 그 수치를 믿어도 되는지 판단할 근거를 함께 담는다.
 * 급여 근거 자료에서는 "조용히 틀린 값"이 "값이 안 나옴"보다 비싸므로, 신뢰성 신호는
 * 리포트 밖(로그·알림)이 아니라 파일 안에 남아야 한다.
 */
public record OverTimeReport(
        YearMonth yearMonth,
        List<OverTimeReportData> rows,
        long unclosedCommuteCount
) {
    public OverTimeReport {
        Objects.requireNonNull(yearMonth, "yearMonth는 null일 수 없습니다.");
        rows = List.copyOf(rows);
    }

    public boolean hasUnclosedCommutes() {
        return unclosedCommuteCount > 0;
    }
}
