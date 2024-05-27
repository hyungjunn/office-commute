package com.company.officecommute.dto.commute.response;

import com.company.officecommute.domain.commute.Detail;

import java.util.List;

public record WorkDurationPerDateResponse(
        List<Detail> details,
        long sumWorkingMinutes
) {
}
