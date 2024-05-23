package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional
    public Long registerTeam(String teamName) {
        boolean isExistTeamName = teamRepository.findByName(teamName);

        Team team = new Team(teamName);
        team.validateUniqueName(isExistTeamName);
        return teamRepository.save(team).getId();
    }

    public Team findTeamById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow();
    }
}
