package com.company.officecommute.domain.holiday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HolidayTest {

    private static final LocalDate LIBERATION_DAY = LocalDate.of(2026, 8, 15);

    @Nested
    @DisplayName("registerFromApi")
    class RegisterFromApi {

        @Test
        @DisplayName("API 적재 행은 항상 공휴일이다")
        void isAlwaysHoliday() {
            Holiday holiday = Holiday.registerFromApi(LIBERATION_DAY, "광복절");

            assertThat(holiday.getHolidayDate()).isEqualTo(LIBERATION_DAY);
            assertThat(holiday.getName()).isEqualTo("광복절");
            assertThat(holiday.getSource()).isEqualTo(HolidaySource.API);
            assertThat(holiday.isHoliday()).isTrue();
            assertThat(holiday.isManual()).isFalse();
        }
    }

    @Nested
    @DisplayName("registerManualHoliday")
    class RegisterManualHoliday {

        @Test
        @DisplayName("긴급 지정은 MANUAL 출처의 공휴일이다")
        void isManualHoliday() {
            Holiday holiday = Holiday.registerManualHoliday(LIBERATION_DAY, "임시공휴일");

            assertThat(holiday.getSource()).isEqualTo(HolidaySource.MANUAL);
            assertThat(holiday.isHoliday()).isTrue();
            assertThat(holiday.isManual()).isTrue();
        }
    }

    @Nested
    @DisplayName("registerManualWorkday")
    class RegisterManualWorkday {

        @Test
        @DisplayName("부정 오버라이드는 MANUAL 출처의 근무일이다")
        void isManualWorkday() {
            Holiday holiday = Holiday.registerManualWorkday(LIBERATION_DAY, "공휴일 아님 정정");

            assertThat(holiday.getSource()).isEqualTo(HolidaySource.MANUAL);
            assertThat(holiday.isHoliday()).isFalse();
            assertThat(holiday.isManual()).isTrue();
        }
    }

    @Test
    @DisplayName("날짜가 없으면 등록할 수 없다")
    void rejectsNullDate() {
        assertThatThrownBy(() -> Holiday.registerFromApi(null, "광복절"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("이름이 비어 있으면 등록할 수 없다")
    void rejectsBlankName() {
        assertThatThrownBy(() -> Holiday.registerManualHoliday(LIBERATION_DAY, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이름의 앞뒤 공백은 제거한다")
    void trimsName() {
        Holiday holiday = Holiday.registerFromApi(LIBERATION_DAY, "  광복절  ");

        assertThat(holiday.getName()).isEqualTo("광복절");
    }
}
