package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayTest {

    private static final LocalDate CONSTITUTION_DAY = LocalDate.of(2026, 7, 17);

    @Test
    @DisplayName("API 적재 행은 출처가 API이고 항상 휴일이다")
    void fromApi() {
        Holiday holiday = Holiday.fromApi(CONSTITUTION_DAY, "제헌절");

        assertThat(holiday.getHolidayDate()).isEqualTo(CONSTITUTION_DAY);
        assertThat(holiday.getName()).isEqualTo("제헌절");
        assertThat(holiday.getSource()).isEqualTo(HolidaySource.API);
        assertThat(holiday.isHoliday()).isTrue();
        assertThat(holiday.isFromApi()).isTrue();
        assertThat(holiday.isManual()).isFalse();
    }

    @Test
    @DisplayName("수동 등록 휴일은 출처가 MANUAL이고 동기화가 갱신할 수 없다")
    void manualHoliday() {
        Holiday holiday = Holiday.manualHoliday(CONSTITUTION_DAY, "제헌절(포털 미반영 보정)");

        assertThat(holiday.getSource()).isEqualTo(HolidaySource.MANUAL);
        assertThat(holiday.isManual()).isTrue();
        assertThat(holiday.isFromApi()).isFalse();
        assertThat(holiday.isHoliday()).isTrue();
        assertThat(holiday.isNegativeOverride()).isFalse();
    }

    @Test
    @DisplayName("부정 오버라이드 행은 휴일이 아님을 주장한다")
    void manualWorkingDay() {
        Holiday holiday = Holiday.manualWorkingDay(CONSTITUTION_DAY, "정상 근무(전사 공지)");

        assertThat(holiday.getSource()).isEqualTo(HolidaySource.MANUAL);
        assertThat(holiday.isHoliday()).isFalse();
        assertThat(holiday.isNegativeOverride()).isTrue();
    }

    @Test
    @DisplayName("회사 지정 휴일은 출처가 COMPANY이고 항상 휴일이다")
    void companyHoliday() {
        Holiday holiday = Holiday.companyHoliday(LocalDate.of(2026, 5, 20), "창립기념일");

        assertThat(holiday.getSource()).isEqualTo(HolidaySource.COMPANY);
        assertThat(holiday.isHoliday()).isTrue();
        assertThat(holiday.isFromApi()).isFalse();
        assertThat(holiday.isManual()).isFalse();
    }

    @Test
    @DisplayName("부정 오버라이드는 MANUAL 전용이다 — API·COMPANY는 휴일이 아님을 표현할 수 없다")
    void negativeOverrideIsManualOnly() {
        assertThatThrownBy(() -> new Holiday(CONSTITUTION_DAY, "제헌절", HolidaySource.API, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MANUAL");
        assertThatThrownBy(() -> new Holiday(CONSTITUTION_DAY, "창립기념일", HolidaySource.COMPANY, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MANUAL");
    }

    @Test
    @DisplayName("동기화는 API 행의 이름을 갱신할 수 있다")
    void updateNameFromApi() {
        Holiday holiday = Holiday.fromApi(CONSTITUTION_DAY, "임시공휴일");

        holiday.updateNameFromApi("제헌절");

        assertThat(holiday.getName()).isEqualTo("제헌절");
    }

    @Test
    @DisplayName("동기화는 사람이 입력한 행을 갱신할 수 없다")
    void updateNameFromApiRejectsHumanEnteredRow() {
        assertThatThrownBy(() -> Holiday.manualHoliday(CONSTITUTION_DAY, "제헌절(수동 보정)").updateNameFromApi("제헌절"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Holiday.companyHoliday(CONSTITUTION_DAY, "창립기념일").updateNameFromApi("제헌절"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("날짜·이름·출처는 비어 있을 수 없고 이름은 트림된다")
    void validatesRequiredFields() {
        assertThatThrownBy(() -> Holiday.fromApi(null, "제헌절"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Holiday.fromApi(CONSTITUTION_DAY, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Holiday(CONSTITUTION_DAY, "제헌절", null, true))
                .isInstanceOf(NullPointerException.class);

        assertThat(Holiday.fromApi(CONSTITUTION_DAY, "  제헌절  ").getName()).isEqualTo("제헌절");
    }

    @Test
    @DisplayName("동등성은 날짜와 출처로 판단한다 — 같은 날짜에 출처별로 한 행씩 공존한다")
    void equalsByDateAndSource() {
        assertThat(Holiday.fromApi(CONSTITUTION_DAY, "제헌절"))
                .isEqualTo(Holiday.fromApi(CONSTITUTION_DAY, "다른 이름"))
                .isNotEqualTo(Holiday.manualHoliday(CONSTITUTION_DAY, "제헌절"))
                .isNotEqualTo(Holiday.fromApi(CONSTITUTION_DAY.plusDays(1), "제헌절"));
    }

    @Test
    @DisplayName("식별자는 (날짜, 출처) 쌍이다")
    void identifier() {
        assertThat(Holiday.fromApi(CONSTITUTION_DAY, "제헌절").getId())
                .isEqualTo(new HolidayId(CONSTITUTION_DAY, HolidaySource.API))
                .isNotEqualTo(new HolidayId(CONSTITUTION_DAY, HolidaySource.MANUAL));
    }
}
