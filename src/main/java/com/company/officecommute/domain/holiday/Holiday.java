package com.company.officecommute.domain.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 공휴일 원장(system of record). 급여 계산은 외부 API가 아니라 이 테이블만 읽는다.
 * <p>
 * 갯수가 아니라 <b>날짜</b> 단위로 저장한다. 갯수만 있으면 주말과 겹치는 공휴일을 걸러낼 수 없고,
 * 대체공휴일·일별 계산도 불가능하다.
 * <p>
 * 이 테이블의 행은 곧 공휴일이다. "공휴일이지만 공휴일로 치지 않는다"는 부정 오버라이드는
 * 두지 않기로 결정했다 — 확정된 법정 공휴일이 사후 취소된 전례가 없고, API 데이터 오류 대응은
 * 실제로 필요해질 때 도입한다.
 */
@Entity
public class Holiday {

    @Id
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HolidaySource source;

    protected Holiday() {
    }

    /**
     * 공공데이터포털 응답으로 적재하는 행. getRestDeInfo(공휴일 정보조회) 응답 항목은 정의상 모두
     * 공휴일이다.
     */
    public static Holiday fromApi(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.API);
    }

    /**
     * 관리자가 직접 등록하는 공휴일. 사후 지정(예: 대통령 선거일)처럼 API 반영이 늦는 날을
     * 즉시 계산에 반영할 때 쓴다. 동기화는 이 행을 갱신·삭제하지 않는다.
     */
    public static Holiday manual(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.MANUAL);
    }

    Holiday(LocalDate holidayDate, String name, HolidaySource source) {
        this.holidayDate = Objects.requireNonNull(holidayDate, "holidayDate는 null일 수 없습니다");
        this.name = validateName(name);
        this.source = Objects.requireNonNull(source, "source는 null일 수 없습니다");
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("holiday의 name은 null이거나 빈 값일 수 없습니다.");
        }
        return name.trim();
    }

    /**
     * 재동기화가 API 행의 이름 변경을 반영할 때 쓴다. MANUAL 행은 동기화 불가침이므로 거부한다.
     */
    public void updateNameFromApi(String name) {
        if (isManual()) {
            throw new IllegalStateException("MANUAL 행은 동기화가 갱신할 수 없습니다. holidayDate=" + holidayDate);
        }
        this.name = validateName(name);
    }

    /**
     * 재동기화가 이 행을 갱신·삭제해도 되는지 판단하는 기준. MANUAL 행은 동기화 대상에서 제외한다.
     */
    public boolean isManual() {
        return source == HolidaySource.MANUAL;
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        Holiday that = (Holiday) object;
        return Objects.equals(holidayDate, that.holidayDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(holidayDate);
    }
}
