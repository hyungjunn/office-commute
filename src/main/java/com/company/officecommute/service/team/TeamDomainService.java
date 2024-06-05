package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.stereotype.Service;

@Service
public class TeamDomainService {

    private final TeamRepository teamRepository;

    public TeamDomainService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Team findTeamByName(String teamName) {
        return teamRepository.findByName(teamName)
                .orElseThrow(() -> new IllegalArgumentException(String.format("해당하는 팀명(%s)이 없습니다.", teamName)));
    }
}
