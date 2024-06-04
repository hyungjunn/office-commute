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
        if (teamRepository.findByName(teamName).isPresent()) {
            throw new IllegalArgumentException(String.format("이미 존재하는 팀명(%s)입니다.", teamName));
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
