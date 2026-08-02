package com.company.officecommute.domain.holiday;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * {@link Holiday}의 복합 식별자 (holiday_date, source).
 * <p>
 * 날짜 단독이 아닌 이유는 같은 날짜에 출처가 다른 행이 공존해야 하기 때문이다 —
 * API가 준 공휴일과 그것을 근무일로 되돌리는 MANUAL 부정 오버라이드가 대표적이다.
 */
public class HolidayId implements Serializable {

    private LocalDate holidayDate;
    private HolidaySource source;

    protected HolidayId() {
    }

    public HolidayId(LocalDate holidayDate, HolidaySource source) {
        this.holidayDate = Objects.requireNonNull(holidayDate, "holidayDate는 null일 수 없습니다");
        this.source = Objects.requireNonNull(source, "source는 null일 수 없습니다");
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public HolidaySource getSource() {
        return source;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        HolidayId that = (HolidayId) object;
        return Objects.equals(holidayDate, that.holidayDate) && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(holidayDate, source);
    }

    @Override
    public String toString() {
        return holidayDate + "/" + source;
    }
}
