package com.company.officecommute.domain.annual_leave;

import java.util.List;
import java.util.Objects;

public class AnnualLeaves {

    private final List<AnnualLeave> annualLeaves;

    public AnnualLeaves(List<AnnualLeave> annualLeaves) {
        this.annualLeaves = annualLeaves;
    }

    public void enroll(List<AnnualLeave> wantedLeaves) {
        if (annualLeaves.stream().anyMatch(wantedLeaves::contains)) {
            throw new IllegalArgumentException("이미 등록된 휴가입니다.");
        }
        annualLeaves.addAll(wantedLeaves);
    }

    public int numberOfLeaves() {
        return annualLeaves.size();
    }

    public boolean isMatchNotEnoughCriteria(int annualLeaveCriteria) {
        return this.annualLeaves.stream()
                .anyMatch(annualLeave
                        -> annualLeave.isNotEnoughForEnroll(annualLeaveCriteria));
    }

    public List<AnnualLeave> getAnnualLeaves() {
        return annualLeaves;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        AnnualLeaves that = (AnnualLeaves) object;
        return Objects.equals(annualLeaves, that.annualLeaves);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(annualLeaves);
    }
}
