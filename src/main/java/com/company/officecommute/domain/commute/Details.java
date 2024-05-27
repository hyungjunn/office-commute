package com.company.officecommute.domain.commute;

import java.util.List;

public class Details {

    private final List<Detail> details;

    public Details(List<Detail> details) {
        this.details = details;
    }

    public long sumWorkingMinutes() {
        return details.stream()
                .mapToLong(Detail::getWorkingMinutes)
                .sum();
    }
}
