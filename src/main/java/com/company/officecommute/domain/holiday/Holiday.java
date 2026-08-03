package com.company.officecommute.domain.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_holiday_date_source", columnNames = {"holiday_date", "source"})
})
@Check(name = "ck_holiday_api_is_holiday", constraints = "source <> 'API' OR is_holiday = TRUE")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holidayId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HolidaySource source;

    @Column(name = "is_holiday", nullable = false)
    private boolean isHoliday;

    protected Holiday() {
    }

    public static Holiday registerFromApi(LocalDate holidayDate, String name) {
        // 응답의 isHoliday는 항상 'Y'인 잉여 필드라 판단 근거로 삼지 않는다.
        return new Holiday(holidayDate, name, HolidaySource.API, true);
    }

    public static Holiday registerManualHoliday(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.MANUAL, true);
    }

    public static Holiday registerManualWorkday(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.MANUAL, false);
    }

    private Holiday(LocalDate holidayDate, String name, HolidaySource source, boolean isHoliday) {
        Objects.requireNonNull(holidayDate, "holidayDate는 null일 수 없습니다");
        Objects.requireNonNull(source, "source는 null일 수 없습니다");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("공휴일 이름은 비어 있을 수 없습니다");
        }
        // 위 팩토리로는 도달할 수 없다. 새 팩토리가 불변식을 깨는 것을 막는 안전망이다.
        if (source == HolidaySource.API && !isHoliday) {
            throw new IllegalArgumentException("API 출처는 휴일 아님으로 저장할 수 없습니다");
        }
        this.holidayDate = holidayDate;
        this.name = name.trim();
        this.source = source;
        this.isHoliday = isHoliday;
    }

    public boolean isManual() {
        return source == HolidaySource.MANUAL;
    }

    public Long getHolidayId() {
        return holidayId;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getName() {
        return name;
    }

    public HolidaySource getSource() {
        return source;
    }

    public boolean isHoliday() {
        return isHoliday;
    }
}
