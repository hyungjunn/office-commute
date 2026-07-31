package com.company.officecommute.repository.holiday;

import com.company.officecommute.domain.holiday.HolidayMonthMarker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HolidayMonthMarkerRepositoryTest {

    @Autowired
    private HolidayMonthMarkerRepository holidayMonthMarkerRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("적재된 월만 existsByMonth가 참이다")
    void existsByMonth() {
        holidayMonthMarkerRepository.save(
                HolidayMonthMarker.of(YearMonth.of(2026, 7), Instant.parse("2026-08-01T00:30:00Z")));
        flushAndClear();

        assertThat(holidayMonthMarkerRepository.existsByMonth(YearMonth.of(2026, 7))).isTrue();
        assertThat(holidayMonthMarkerRepository.existsByMonth(YearMonth.of(2026, 8))).isFalse();
    }

    /**
     * 정기 재동기화가 같은 월을 반복 적재해도 마커는 한 행으로 유지되고 동기화 시각만 갱신된다
     * (idempotent 재동기화의 근거).
     */
    @Test
    @DisplayName("같은 월을 다시 적재하면 행이 늘지 않고 동기화 시각이 갱신된다")
    void resyncUpdatesSyncedAtWithoutNewRow() {
        YearMonth month = YearMonth.of(2026, 7);
        holidayMonthMarkerRepository.save(HolidayMonthMarker.of(month, Instant.parse("2026-08-01T00:30:00Z")));
        flushAndClear();

        Instant resyncedAt = Instant.parse("2026-08-02T00:30:00Z");
        holidayMonthMarkerRepository.save(HolidayMonthMarker.of(month, resyncedAt));
        flushAndClear();

        assertThat(holidayMonthMarkerRepository.count()).isEqualTo(1);
        assertThat(holidayMonthMarkerRepository.findById(month.atDay(1)).orElseThrow().getSyncedAt())
                .isEqualTo(resyncedAt);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
