package com.company.officecommute.domain.holiday;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toMap;

/**
 * 재동기화가 원장을 어떻게 바꾸는지 — 적용 전에 계산해 로그로 남기는 감사 자료.
 * <p>
 * 매일 도는 동기화는 이미 급여 계산에 쓰인 과거 월도 다시 쓴다. 그 자체는 필요한 동작이지만
 * (임시공휴일은 연중에 늘어난다), 아무 흔적 없이 바뀌면 "지난달 리포트가 왜 이 값이었는지"를
 * 나중에 되짚을 수 없다. 무엇이 언제 바뀌었는지는 남아야 한다.
 */
public record HolidayLedgerDiff(
        List<LocalDate> added,
        List<LocalDate> removed,
        List<LocalDate> renamed,
        List<LocalDate> absorbedManual
) {

    /**
     * 기존 원장 행과 새 API 응답을 견준다.
     *
     * @param existingRows  대상 연도의 모든 행 (출처 무관)
     * @param apiNameByDate API 응답의 날짜 → 이름
     */
    public static HolidayLedgerDiff between(Collection<Holiday> existingRows, Map<LocalDate, String> apiNameByDate) {
        Map<LocalDate, String> existingApiNames = existingRows.stream()
                .filter(Holiday::isFromApi)
                .collect(toMap(Holiday::getHolidayDate, Holiday::getName));

        List<LocalDate> added = apiNameByDate.keySet().stream()
                .filter(date -> !existingApiNames.containsKey(date))
                .sorted(naturalOrder())
                .toList();
        List<LocalDate> removed = existingApiNames.keySet().stream()
                .filter(date -> !apiNameByDate.containsKey(date))
                .sorted(naturalOrder())
                .toList();
        List<LocalDate> renamed = existingApiNames.entrySet().stream()
                .filter(entry -> apiNameByDate.containsKey(entry.getKey()))
                .filter(entry -> !entry.getValue().equals(apiNameByDate.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .sorted(naturalOrder())
                .toList();
        // 흡수는 관리자가 넣은 행이 사라지는 일이라 API 행 변경보다 오히려 더 남길 가치가 있다.
        List<LocalDate> absorbedManual = existingRows.stream()
                .filter(row -> row.isManual() && row.isHoliday())
                .map(Holiday::getHolidayDate)
                .filter(apiNameByDate::containsKey)
                .sorted(naturalOrder())
                .toList();

        return new HolidayLedgerDiff(added, removed, renamed, absorbedManual);
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && renamed.isEmpty() && absorbedManual.isEmpty();
    }

    /**
     * 기준 월보다 앞선 달에 걸린 변경. 그 달의 리포트는 이미 나갔을 수 있으므로 재산정 판단이 필요하다.
     */
    public List<LocalDate> changedDatesBefore(YearMonth month) {
        return changedDates()
                .filter(date -> YearMonth.from(date).isBefore(month))
                .sorted(naturalOrder())
                .toList();
    }

    private Stream<LocalDate> changedDates() {
        return Stream.of(added, removed, renamed, absorbedManual)
                .flatMap(List::stream)
                .distinct();
    }

    @Override
    public String toString() {
        return "추가=" + added + ", 삭제=" + removed + ", 이름변경=" + renamed + ", 수동행흡수=" + absorbedManual;
    }
}
