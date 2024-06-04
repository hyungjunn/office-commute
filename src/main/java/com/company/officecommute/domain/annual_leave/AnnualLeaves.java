package com.company.officecommute.domain.annual_leave;

import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;

import java.util.List;

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

    public List<AnnualLeaveEnrollmentResponse> toAnnualLeaveEnrollmentResponse() {
        return this.annualLeaves.stream()
                .map(it -> new AnnualLeaveEnrollmentResponse(it.getId(), it.getDate()))
                .toList();
    }

    public List<AnnualLeave> getAnnualLeaves() {
        return annualLeaves;
    }

}
