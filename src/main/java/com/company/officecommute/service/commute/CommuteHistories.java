package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.Detail;
import com.company.officecommute.domain.commute.Details;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;

import java.util.List;

public class CommuteHistories {

    private final List<CommuteHistory> commuteHistories;

    public CommuteHistories(List<CommuteHistory> commuteHistories) {
        this.commuteHistories = commuteHistories;
    }

    public WorkDurationPerDateResponse toWorkDurationPerDateResponse() {
        List<Detail> details = toDetails();
        long sumWorkingMinutes = new Details(details).sumWorkingMinutes();
        // TODO 여기서 dto 변환 처리를 해줘도 되는지 생각해보기
        return new WorkDurationPerDateResponse(details, sumWorkingMinutes);
    }

    private List<Detail> toDetails() {
        return commuteHistories
                .stream()
                .map(CommuteHistory::toDetail)
                .toList();
    }
}
