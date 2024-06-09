package com.company.officecommute.domain.commute;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WorkingMinutesTest {

    @Test
    void instantiate() {
        assertThat(new WorkingMinutes(1000L)).isEqualTo(new WorkingMinutes(1000L));
    }

    @Test
    void testException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkingMinutes(-1L));
    }
}
