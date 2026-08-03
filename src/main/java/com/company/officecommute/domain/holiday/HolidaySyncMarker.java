package com.company.officecommute.domain.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.Year;
import java.util.Objects;

/**
 * 해당 연도의 공휴일이 원장에 적재되었다는 사실. 마커가 없는 연도의 월은 계산을 거부한다
 * — 이것이 없으면 "공휴일 0개인 달"과 "아직 적재 안 된 달"을 구분할 수 없다.
 */
@Entity
@Table(name = "holiday_sync_marker")
public class HolidaySyncMarker {

    @Id
    @Column(name = "sync_year", nullable = false)
    private int syncYear;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected HolidaySyncMarker() {
    }

    public static HolidaySyncMarker mark(Year syncYear, Instant syncedAt) {
        return new HolidaySyncMarker(syncYear, syncedAt);
    }

    private HolidaySyncMarker(Year syncYear, Instant syncedAt) {
        Objects.requireNonNull(syncYear, "syncYear는 null일 수 없습니다");
        Objects.requireNonNull(syncedAt, "syncedAt은 null일 수 없습니다");
        this.syncYear = syncYear.getValue();
        this.syncedAt = syncedAt;
    }

    public Year getSyncYear() {
        return Year.of(syncYear);
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
