package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;

public class Teams {
    public static Team createTeamWithTeamName(String teamName) {
        return new Team(1L, teamName, null, 0);
    }
}
