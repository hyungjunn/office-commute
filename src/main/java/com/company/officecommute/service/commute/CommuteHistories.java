package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.DailyWorkDuration;
import com.company.officecommute.domain.commute.DailyWorkDurations;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;

import java.time.Instant;
import java.util.List;

public class CommuteHistories {

    private final List<CommuteHistory> commuteHistories;

    public CommuteHistories(List<CommuteHistory> commuteHistories) {
        this.commuteHistories = commuteHistories;
    }

    public WorkDurationPerDateResponse toWorkDurationPerDateResponse(Instant now) {
        long sumWorkingMinutes = new DailyWorkDurations(toDailyWorkDurations()).sumWorkingMinutes();
        return new WorkDurationPerDateResponse(toDetails(now), sumWorkingMinutes);
    }

    private List<CommuteDetailResponse> toDetails(Instant now) {
        return commuteHistories
                .stream()
                .map(commuteHistory -> CommuteDetailResponse.from(commuteHistory, now))
                .toList();
    }

    private List<DailyWorkDuration> toDailyWorkDurations() {
        return commuteHistories
                .stream()
                .map(CommuteHistory::toDailyWorkDuration)
                .toList();
    }
}
