package com.company.officecommute.domain.annual_leave;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AnnualLeavesTest {

    @Test
    void testEnroll() {
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>());
        List<AnnualLeave> wantedLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));

        annualLeaves.enroll(new ArrayList<>(wantedLeaves));

        assertThat(annualLeaves.numberOfLeaves()).isEqualTo(1);
    }

    @Test
    void testEnrollExceptionWhenExist() {
        List<AnnualLeave> existingLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>(existingLeaves));
        List<AnnualLeave> wantedLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));

        annualLeaves.enroll(new ArrayList<>(wantedLeaves));

        Assertions.assertThatIllegalArgumentException().isThrownBy(()
        -> annualLeaves.enroll(new ArrayList<>(wantedLeaves)));
    }

    @Test
    void testIsMatchNotEnoughCriteria() {
        List<AnnualLeave> existingLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now()));
        AnnualLeaves annualLeaves = new AnnualLeaves(new ArrayList<>(existingLeaves));
        int annualLeaveCriteria = 1;

        boolean result = annualLeaves.isMatchNotEnoughCriteria(annualLeaveCriteria);

        assertThat(result).isTrue();
    }
}
