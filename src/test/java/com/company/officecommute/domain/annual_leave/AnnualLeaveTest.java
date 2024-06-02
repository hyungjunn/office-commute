package com.company.officecommute.domain.annual_leave;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AnnualLeaveTest {

    @Test
    void testInstantiate() {
        new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10));

        assertThatIllegalArgumentException().isThrownBy(()
                -> new AnnualLeave(1L, 1L, LocalDate.now().minusDays(10)));
    }
}
