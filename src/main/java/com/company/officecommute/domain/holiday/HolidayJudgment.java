package com.company.officecommute.domain.holiday;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

/**
 * 같은 날짜에 여러 출처의 행이 공존할 때 그 날이 휴일인지 판정한다.
 * <p>
 * 규칙은 두 줄이다:
 * <ol>
 *     <li>(MANUAL, is_holiday=false) 행이 있으면 근무일 — 부정 오버라이드가 항상 이긴다.</li>
 *     <li>그렇지 않고 is_holiday=true인 행이 하나라도 있으면 휴일.</li>
 * </ol>
 * 부정 오버라이드를 먼저 보는 이유는 그것이 "다른 출처가 틀렸다"는 사람의 명시적 판단이기
 * 때문이다. API 행과 동점 처리하면 관리자가 API 오류를 되돌릴 방법이 없어진다.
 */
public final class HolidayJudgment {

    private HolidayJudgment() {
    }

    /**
     * 주어진 행들에서 실제로 휴일인 날짜만 추린다. 판정에 진 날짜(부정 오버라이드가 걸린 날)는
     * 행이 있어도 결과에 포함되지 않는다.
     */
    public static Set<LocalDate> holidayDatesOf(Collection<Holiday> rows) {
        Map<LocalDate, List<Holiday>> rowsByDate = rows.stream()
                .collect(groupingBy(Holiday::getHolidayDate));

        return rowsByDate.entrySet().stream()
                .filter(entry -> isHoliday(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(toSet());
    }

    private static boolean isHoliday(List<Holiday> rowsOnDate) {
        if (rowsOnDate.stream().anyMatch(Holiday::isNegativeOverride)) {
            return false;
        }
        return rowsOnDate.stream().anyMatch(Holiday::isHoliday);
    }
}
