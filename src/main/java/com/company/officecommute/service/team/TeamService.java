package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional
    public Long registerTeam(String teamName) {
        Team namedTeam = teamRepository.findByName(teamName);
        if (namedTeam != null) {
            throw new IllegalArgumentException("이미 존재하는 팀명입니다.");
        }
        Team team = new Team(teamName);
        return teamRepository.save(team).getTeamId();
    }

    @Transactional(readOnly = true)
    public List<TeamFindResponse> findTeam() {
        return teamRepository.findTeam()
                .stream()
                .map(TeamFindResponse::from)
                .toList();
    }
}
