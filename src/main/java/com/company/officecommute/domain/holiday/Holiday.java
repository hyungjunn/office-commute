package com.company.officecommute.domain.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 공휴일 원장(system of record). 급여 계산은 외부 API가 아니라 이 테이블만 읽는다.
 * <p>
 * 갯수가 아니라 <b>날짜</b> 단위로 저장한다. 갯수만 있으면 주말과 겹치는 공휴일을 걸러낼 수 없고,
 * 대체공휴일·일별 계산도 불가능하다.
 * <p>
 * 한 행은 "이 날짜에 대해 이 출처가 내린 판단"이다 — 행의 존재만으로 휴일이 되지는 않는다.
 * V8 시점의 "행이 곧 공휴일이다"라는 설계는 V9에서 뒤집혔다: {@code isHoliday=false}인
 * MANUAL 행이 부정 오버라이드(휴일 → 근무일)를 표현하고, 같은 날짜에 출처가 다른 행이 공존한다.
 * 날짜별 최종 판정은 {@link HolidayJudgment}가 내린다.
 *
 * @see HolidayId 복합 식별자 (holiday_date, source)
 */
@Entity
@IdClass(HolidayId.class)
public class Holiday {

    @Id
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HolidaySource source;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_holiday", nullable = false)
    private boolean isHoliday;

    protected Holiday() {
    }

    /**
     * 공공데이터포털 응답으로 적재하는 행. getRestDeInfo(공휴일 정보조회) 응답 항목은 정의상 모두
     * 공휴일이다.
     */
    public static Holiday fromApi(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.API, true);
    }

    /**
     * 관리자가 직접 등록하는 공휴일. 사후 지정(예: 임시공휴일)처럼 API 반영이 늦는 날을
     * 즉시 계산에 반영할 때 쓴다. 나중에 API가 같은 날짜를 주면 동기화가 이 행을 삭제한다 —
     * API 행이 같은 판단을 대신하므로 남겨 둘 이유가 없다.
     */
    public static Holiday manualHoliday(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.MANUAL, true);
    }

    /**
     * 부정 오버라이드 — 다른 행이 휴일이라고 해도 이 날은 근무일이다. API 데이터 오류나
     * 회사 사정으로 정상 근무하는 공휴일을 표현한다. 동기화는 이 행을 절대 지우지 않는다.
     */
    public static Holiday manualWorkingDay(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.MANUAL, false);
    }

    /**
     * 회사 지정 휴일(창립기념일 등). 법정 공휴일이 아니므로 API와 무관하게 존재하며,
     * 동기화가 건드리지 않는다.
     */
    public static Holiday companyHoliday(LocalDate holidayDate, String name) {
        return new Holiday(holidayDate, name, HolidaySource.COMPANY, true);
    }

    Holiday(LocalDate holidayDate, String name, HolidaySource source, boolean isHoliday) {
        this.holidayDate = Objects.requireNonNull(holidayDate, "holidayDate는 null일 수 없습니다");
        this.name = validateName(name);
        this.source = Objects.requireNonNull(source, "source는 null일 수 없습니다");
        this.isHoliday = validateNegativeOverride(source, isHoliday);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("holiday의 name은 null이거나 빈 값일 수 없습니다.");
        }
        return name.trim();
    }

    /**
     * 부정 오버라이드는 MANUAL 전용이다. API 응답 항목은 정의상 공휴일이고, COMPANY는 회사가
     * 휴일로 지정한 날이므로 둘 다 "휴일이 아님"을 표현할 수 없다. DDL의
     * {@code ck_holiday_negative_override_is_manual}과 같은 규칙이다.
     */
    private boolean validateNegativeOverride(HolidaySource source, boolean isHoliday) {
        if (!isHoliday && source != HolidaySource.MANUAL) {
            throw new IllegalArgumentException(
                    "부정 오버라이드는 MANUAL 출처만 가능합니다. source=" + source + ", holidayDate=" + holidayDate);
        }
        return isHoliday;
    }

    /**
     * 재동기화가 API 행의 이름 변경을 반영할 때 쓴다. 사람이 입력한 행은 동기화가 갱신할 수 없다.
     */
    public void updateNameFromApi(String name) {
        if (!isFromApi()) {
            throw new IllegalStateException(
                    "동기화는 " + source + " 행을 갱신할 수 없습니다. holidayDate=" + holidayDate);
        }
        this.name = validateName(name);
    }

    /**
     * 연 단위 재동기화가 통째로 갈아끼우는 대상인지. API 행만 해당하고, 사람이 입력한
     * MANUAL·COMPANY 행은 제외된다.
     */
    public boolean isFromApi() {
        return source == HolidaySource.API;
    }

    public boolean isManual() {
        return source == HolidaySource.MANUAL;
    }

    /**
     * 이 행이 "휴일이 아니다"를 주장하는지. 날짜 판정에서 다른 어떤 행보다 우선한다.
     */
    public boolean isNegativeOverride() {
        return !isHoliday;
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

    public HolidayId getId() {
        return new HolidayId(holidayDate, source);
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
        return Objects.equals(holidayDate, that.holidayDate) && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(holidayDate, source);
    }
}
