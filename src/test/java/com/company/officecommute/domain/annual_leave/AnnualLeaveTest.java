package com.company.officecommute.domain.annual_leave;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AnnualLeaveTest {

    @Test
    void testInstantiate() {
        new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10));

        assertThatIllegalArgumentException().isThrownBy(()
                -> new AnnualLeave(1L, 1L, LocalDate.now().minusDays(10)));
    }

    @Test
    void testIsNotEnoughForEnroll() {
        AnnualLeave annualLeave = new AnnualLeave(1L, 1L, LocalDate.now().plusDays(9));

        assertThat(annualLeave.isNotEnoughForEnroll(10)).isTrue();
        assertThat(annualLeave.isNotEnoughForEnroll(9)).isFalse();
    }
}
