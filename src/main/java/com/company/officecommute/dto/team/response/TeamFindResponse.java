package com.company.officecommute.dto.team.response;

import com.company.officecommute.domain.team.Team;

public record TeamFindResponse(
        String name,
        String managerName,
        int memberCount
) {
    public static TeamFindResponse from(Team team) {
        return new TeamFindResponse(
                team.getName(),
                team.getManagerName(),
                team.getMemberCount()
        );
    }
}
