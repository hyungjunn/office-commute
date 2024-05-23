package com.company.officecommute.domain.team;

import com.company.officecommute.service.team.Teams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamTest {

    @NullAndEmptySource
    @ParameterizedTest
    void testTeamNameException(String expected) {
        assertThatThrownBy(() -> Teams.createTeamWithTeamName(expected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", expected));
    }

    @Test
    void testTeamNameUniqueException() {
        Team aTeam = Teams.createTeamWithTeamName("ATeam");
        assertThatThrownBy(() -> aTeam.validateUniqueName(true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 팀명입니다.");
    }
}
