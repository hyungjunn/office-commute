package com.company.officecommute.dto.overtime.response;

import java.util.Objects;

/**
 * 급여 근거 자료의 한 행. 빈 칸이 조용히 섞이는 것보다 생성 시점에 실패하는 편이 싸므로
 * 식별 정보는 null을 허용하지 않고, 수치는 primitive 로 두어 언박싱 NPE 자체를 없앤다.
 */
public record OverTimeReportData(
        String employeeCode,
        String employeeName,
        String teamName,
        long overTimeMinutes,
        long overTimePay
) {
    public OverTimeReportData {
        Objects.requireNonNull(employeeCode, "employeeCode는 null일 수 없습니다.");
        Objects.requireNonNull(employeeName, "employeeName은 null일 수 없습니다.");
        Objects.requireNonNull(teamName, "teamName은 null일 수 없습니다.");
    }
}
