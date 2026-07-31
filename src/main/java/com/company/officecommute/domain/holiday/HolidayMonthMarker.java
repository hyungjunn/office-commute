package com.company.officecommute.domain.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 월 단위 적재 마커 — "공휴일 0개인 달"(4월·11월은 정상적으로 0개)과 "아직 적재 안 된 달"을 구분한다.
 * <p>
 * 마커가 없는 달은 원장의 완전성을 보장할 수 없으므로 계산을 거부한다. 이 구분이 없으면
 * 빈 원장이 "공휴일 0개"로 해석되어 silent fail-open이 DB로 이사한다.
 * <p>
 * 마커는 동기화 성공만이 세운다. 관리자의 수동 공휴일 추가는 그 달의 나머지 공휴일이 적재됐다는
 * 보장이 아니므로 마커를 세우지 않는다.
 */
@Entity
public class HolidayMonthMarker {

    @Id
    @Column(name = "marker_month", nullable = false)
    private LocalDate markerMonth;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected HolidayMonthMarker() {
    }

    public static HolidayMonthMarker of(YearMonth month, Instant syncedAt) {
        return new HolidayMonthMarker(month, syncedAt);
    }

    HolidayMonthMarker(YearMonth month, Instant syncedAt) {
        this.markerMonth = Objects.requireNonNull(month, "month는 null일 수 없습니다").atDay(1);
        this.syncedAt = Objects.requireNonNull(syncedAt, "syncedAt은 null일 수 없습니다");
    }

    public YearMonth getMonth() {
        return YearMonth.from(markerMonth);
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        HolidayMonthMarker that = (HolidayMonthMarker) object;
        return Objects.equals(markerMonth, that.markerMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(markerMonth);
    }
}
