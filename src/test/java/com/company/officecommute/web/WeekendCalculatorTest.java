package com.company.officecommute.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class WeekendCalculatorTest {

    @Test
    void testIsWeekend() {
        assertThat(WeekendCalculator.isWeekend(LocalDate.of(2024, 6, 8))).isTrue();
        assertThat(WeekendCalculator.isWeekend(LocalDate.of(2024, 6, 9))).isTrue();

        assertThat(WeekendCalculator.isWeekend(LocalDate.of(2024, 6, 10))).isFalse();
    }

    @Test
    void testCountNumberOfWeekends() {
        assertThat(WeekendCalculator.countNumberOfWeekends(YearMonth.of(2024, 6))).isEqualTo(10);
    }
}
