package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayTest {

    private static final LocalDate CONSTITUTION_DAY = LocalDate.of(2026, 7, 17);

    @Test
    @DisplayName("API 적재 행은 출처가 API다")
    void fromApi() {
        Holiday holiday = Holiday.fromApi(CONSTITUTION_DAY, "제헌절");

        assertThat(holiday.getHolidayDate()).isEqualTo(CONSTITUTION_DAY);
        assertThat(holiday.getName()).isEqualTo("제헌절");
        assertThat(holiday.getSource()).isEqualTo(HolidaySource.API);
        assertThat(holiday.isManual()).isFalse();
    }

    @Test
    @DisplayName("수동 등록 행은 출처가 MANUAL이고 동기화 불가침 대상이다")
    void manual() {
        Holiday holiday = Holiday.manual(CONSTITUTION_DAY, "제헌절(포털 미반영 보정)");

        assertThat(holiday.getSource()).isEqualTo(HolidaySource.MANUAL);
        assertThat(holiday.isManual()).isTrue();
    }

    @Test
    @DisplayName("동기화는 API 행의 이름을 갱신할 수 있다")
    void updateNameFromApi() {
        Holiday holiday = Holiday.fromApi(CONSTITUTION_DAY, "임시공휴일");

        holiday.updateNameFromApi("제헌절");

        assertThat(holiday.getName()).isEqualTo("제헌절");
    }

    @Test
    @DisplayName("동기화는 MANUAL 행을 갱신할 수 없다")
    void updateNameFromApiRejectsManualRow() {
        Holiday holiday = Holiday.manual(CONSTITUTION_DAY, "제헌절(수동 보정)");

        assertThatThrownBy(() -> holiday.updateNameFromApi("제헌절"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("날짜·이름·출처는 비어 있을 수 없고 이름은 트림된다")
    void validatesRequiredFields() {
        assertThatThrownBy(() -> Holiday.fromApi(null, "제헌절"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Holiday.fromApi(CONSTITUTION_DAY, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Holiday(CONSTITUTION_DAY, "제헌절", null))
                .isInstanceOf(NullPointerException.class);

        assertThat(Holiday.fromApi(CONSTITUTION_DAY, "  제헌절  ").getName()).isEqualTo("제헌절");
    }

    @Test
    @DisplayName("동등성은 날짜로 판단한다 — 하루에 한 행만 존재한다")
    void equalsByDate() {
        assertThat(Holiday.fromApi(CONSTITUTION_DAY, "제헌절"))
                .isEqualTo(Holiday.manual(CONSTITUTION_DAY, "다른 이름"))
                .isNotEqualTo(Holiday.fromApi(CONSTITUTION_DAY.plusDays(1), "제헌절"));
    }
}
