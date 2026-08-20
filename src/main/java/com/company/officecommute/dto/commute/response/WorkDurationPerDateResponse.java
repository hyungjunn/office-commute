package com.company.officecommute.dto.commute.response;

import java.util.List;

public record WorkDurationPerDateResponse(
        List<CommuteDetailResponse> details,
        long sumWorkingMinutes
) {
}
