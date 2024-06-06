package com.company.officecommute.domain.commute;

import java.util.Objects;

public class WorkingMinutes {

    private final long workingMinutes;

    public WorkingMinutes(long workingMinutes) {
        if (workingMinutes < 0) {
            throw new IllegalArgumentException("근무 시간은 0 이상이어야 합니다.");
        }
        this.workingMinutes = workingMinutes;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        WorkingMinutes that = (WorkingMinutes) object;
        return workingMinutes == that.workingMinutes;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(workingMinutes);
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }
}
