package com.company.officecommute.domain.commute;

import java.time.LocalDate;

public class Detail {

    private final LocalDate date;

    private final long workingMinutes;

    public Detail(LocalDate date, long workingMinutes) {
        this.date = date;
        this.workingMinutes = workingMinutes;
    }

    public long getWorkingMinutes() {
        return workingMinutes;
    }
}
