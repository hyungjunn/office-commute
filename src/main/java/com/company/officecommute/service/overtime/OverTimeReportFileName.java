package com.company.officecommute.service.overtime;

import java.time.YearMonth;

/**
 * 초과근무 보고서 엑셀 파일명. 온디맨드 다운로드(컨트롤러)와 매월 배치 메일 첨부가
 * <b>같은 이름</b>을 내보내야 대표·관리자가 두 경로에서 받은 파일을 같은 것으로 식별한다.
 * 양쪽에 문자열을 따로 두면 한쪽만 바뀌는 순간 조용히 갈라지므로 한 곳에서만 만든다.
 */
public final class OverTimeReportFileName {

    private OverTimeReportFileName() {
    }

    public static String of(YearMonth yearMonth) {
        return yearMonth.getYear() + "년" + yearMonth.getMonthValue() + "월_초과근무보고서.xlsx";
    }
}
