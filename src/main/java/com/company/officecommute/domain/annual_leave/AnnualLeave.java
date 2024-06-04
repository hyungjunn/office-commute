package com.company.officecommute.domain.annual_leave;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class AnnualLeave {

    @Id
    @GeneratedValue
    private Long id;
    private Long employeeId;
    private LocalDate wantedDate;

    protected AnnualLeave() {
    }

    public AnnualLeave(Long employeeId, LocalDate wantedDate) {
        this(null, employeeId, wantedDate);
    }

    public AnnualLeave(Long id, Long employeeId, LocalDate wantedDate) {
        if (wantedDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(String.format("(%s)는 지난 날짜입니다.", wantedDate));
        }
        this.id = id;
        this.employeeId = employeeId;
        this.wantedDate = wantedDate;
    }

    public boolean isNotEnoughForEnroll(int annualLeaveCriteria) {
        return wantedDate.isBefore(LocalDate.now().plusDays(annualLeaveCriteria));
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return wantedDate;
    }
}
