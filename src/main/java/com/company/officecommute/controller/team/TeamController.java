package com.company.officecommute.controller.team;

import com.company.officecommute.dto.team.request.TeamRegisterRequest;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.service.team.TeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping("/team")
    public Long registerTeam(@RequestBody TeamRegisterRequest request) {
        return teamService.registerTeam(request.teamName());
    }

    @GetMapping("/team")
    public List<TeamFindResponse> findAllTeam() {
        return teamService.findTeam();
    }
}
