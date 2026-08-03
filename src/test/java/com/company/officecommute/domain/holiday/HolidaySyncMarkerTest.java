package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidaySyncMarkerTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-08-03T18:00:00Z");

    @Test
    @DisplayName("연도와 동기화 시각으로 마커를 세운다")
    void mark() {
        HolidaySyncMarker marker = HolidaySyncMarker.mark(Year.of(2026), SYNCED_AT);

        assertThat(marker.getSyncYear()).isEqualTo(Year.of(2026));
        assertThat(marker.getSyncedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    @DisplayName("연도가 없으면 마커를 세울 수 없다")
    void rejectsNullYear() {
        assertThatThrownBy(() -> HolidaySyncMarker.mark(null, SYNCED_AT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("동기화 시각이 없으면 마커를 세울 수 없다")
    void rejectsNullSyncedAt() {
        assertThatThrownBy(() -> HolidaySyncMarker.mark(Year.of(2026), null))
                .isInstanceOf(NullPointerException.class);
    }
}
