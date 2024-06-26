package com.company.officecommute.service.team;

import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.repository.team.TeamRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @InjectMocks
    private TeamService teamService;

    @Mock
    private TeamRepository teamRepository;

    @Test
    void testRegisterTeam() {
        Team team = Teams.createTeamWithTeamName("ATeam");
        BDDMockito.given(teamRepository.save(any(Team.class)))
                .willReturn(team);

        Long teamId = teamService.registerTeam("ATeam");

        assertThat(teamId).isEqualTo(1L);
    }

    @Test
    void testFindTeam() {
        Team team = Teams.createTeamWithTeamName("ATeam");
        BDDMockito.given(teamRepository.findTeam())
                .willReturn(List.of(team));

        List<TeamFindResponse> teams = teamService.findTeam();

        assertThat(teams.size()).isEqualTo(1);
        assertThat(teams.contains(TeamFindResponse.from(team))).isTrue();
    }

    @Test
    void testRegisterTeamException() {
        String teamName = "ATeam";

        BDDMockito.given(teamRepository.findByName(teamName))
                .willReturn(Optional.of(Teams.createTeamWithTeamName(teamName)));

        Assertions.assertThatThrownBy(() -> teamService.registerTeam(teamName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("이미 존재하는 팀명(%s)입니다.", teamName));
    }
}
