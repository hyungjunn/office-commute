package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HolidayLedgerDiffTest {

    private static final LocalDate NEW_YEAR = LocalDate.of(2026, 1, 1);
    private static final LocalDate TEMPORARY_HOLIDAY = LocalDate.of(2026, 3, 10);
    private static final LocalDate CONSTITUTION_DAY = LocalDate.of(2026, 7, 17);

    @Test
    @DisplayName("같은 내용으로 다시 동기화하면 변경이 없다")
    void detectsNoChangeOnIdenticalResponse() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(Holiday.fromApi(NEW_YEAR, "1월1일")),
                Map.of(NEW_YEAR, "1월1일"));

        assertThat(diff.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("응답에 새로 생긴 날짜는 추가로 잡는다")
    void detectsAddedDate() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(Holiday.fromApi(NEW_YEAR, "1월1일")),
                Map.of(NEW_YEAR, "1월1일", TEMPORARY_HOLIDAY, "임시공휴일"));

        assertThat(diff.added()).containsExactly(TEMPORARY_HOLIDAY);
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("응답에서 사라진 API 행은 삭제로 잡는다")
    void detectsRemovedDate() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(Holiday.fromApi(NEW_YEAR, "1월1일"), Holiday.fromApi(TEMPORARY_HOLIDAY, "임시공휴일")),
                Map.of(NEW_YEAR, "1월1일"));

        assertThat(diff.removed()).containsExactly(TEMPORARY_HOLIDAY);
        assertThat(diff.added()).isEmpty();
    }

    @Test
    @DisplayName("이름만 바뀐 날짜는 이름변경으로 잡는다")
    void detectsRenamedDate() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(Holiday.fromApi(CONSTITUTION_DAY, "임시공휴일")),
                Map.of(CONSTITUTION_DAY, "제헌절"));

        assertThat(diff.renamed()).containsExactly(CONSTITUTION_DAY);
        assertThat(diff.added()).isEmpty();
        assertThat(diff.removed()).isEmpty();
    }

    /**
     * 관리자가 직접 넣은 행이 사라지는 일이라 API 행 변경보다 오히려 더 남길 가치가 있다.
     */
    @Test
    @DisplayName("흡수되는 수동 등록 휴일을 따로 잡는다")
    void detectsAbsorbedManualHoliday() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(Holiday.manualHoliday(TEMPORARY_HOLIDAY, "임시공휴일(포털 미반영 보정)")),
                Map.of(TEMPORARY_HOLIDAY, "임시공휴일"));

        assertThat(diff.absorbedManual()).containsExactly(TEMPORARY_HOLIDAY);
        // 같은 날짜에 API 행이 새로 생기는 것이므로 추가로도 잡힌다.
        assertThat(diff.added()).containsExactly(TEMPORARY_HOLIDAY);
    }

    @Test
    @DisplayName("살아남는 행은 변경으로 잡지 않는다 — 부정 오버라이드와 회사 지정 휴일")
    void ignoresSurvivingRows() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(
                        Holiday.manualWorkingDay(NEW_YEAR, "정상 근무(전사 공지)"),
                        Holiday.companyHoliday(LocalDate.of(2026, 5, 20), "창립기념일"),
                        Holiday.fromApi(NEW_YEAR, "1월1일")
                ),
                Map.of(NEW_YEAR, "1월1일"));

        assertThat(diff.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("지난 달에 걸린 변경만 따로 추린다 — 그 달 리포트는 이미 나갔을 수 있다")
    void picksChangesInSettledMonths() {
        HolidayLedgerDiff diff = HolidayLedgerDiff.between(
                List.of(Holiday.fromApi(NEW_YEAR, "1월1일")),
                Map.of(TEMPORARY_HOLIDAY, "임시공휴일", CONSTITUTION_DAY, "제헌절"));

        assertThat(diff.changedDatesBefore(YearMonth.of(2026, 7)))
                .containsExactly(NEW_YEAR, TEMPORARY_HOLIDAY);
        assertThat(diff.changedDatesBefore(YearMonth.of(2026, 1))).isEmpty();
    }
}
