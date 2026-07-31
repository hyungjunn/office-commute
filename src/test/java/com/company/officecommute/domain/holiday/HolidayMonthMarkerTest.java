package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayMonthMarkerTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-08-01T00:30:00Z");

    @Test
    @DisplayName("마커는 월 단위로 만들어지고 대상 월을 그대로 돌려준다")
    void createsForMonth() {
        HolidayMonthMarker marker = HolidayMonthMarker.of(YearMonth.of(2026, 7), SYNCED_AT);

        assertThat(marker.getMonth()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(marker.getSyncedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    @DisplayName("월과 동기화 시각은 null일 수 없다")
    void validatesRequiredFields() {
        assertThatThrownBy(() -> HolidayMonthMarker.of(null, SYNCED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HolidayMonthMarker.of(YearMonth.of(2026, 7), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("동등성은 월로 판단한다 — 한 달에 마커 하나만 존재한다")
    void equalsByMonth() {
        assertThat(HolidayMonthMarker.of(YearMonth.of(2026, 7), SYNCED_AT))
                .isEqualTo(HolidayMonthMarker.of(YearMonth.of(2026, 7), SYNCED_AT.plusSeconds(60)))
                .isNotEqualTo(HolidayMonthMarker.of(YearMonth.of(2026, 8), SYNCED_AT));
    }
}
