package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.team.request.TeamRegisterRequest;
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
    public void registerTeam(TeamRegisterRequest request) {
        teamRepository.findByName(request.teamName()).ifPresent(team -> {
            throw new IllegalArgumentException("이미 존재하는 팀입니다.");
        });
        teamRepository.save(new Team(request.teamName()));
    }

    @Transactional(readOnly = true)
    public List<TeamFindResponse> findTeam() {
        return teamRepository.findAll().stream()
                .map(TeamFindResponse::from)
                .toList();
    }
}
