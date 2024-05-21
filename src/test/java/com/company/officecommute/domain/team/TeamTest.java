package com.company.officecommute.domain.team;

import com.company.officecommute.service.team.Teams;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class TeamTest {

    @NullAndEmptySource
    @ParameterizedTest
    void testTeamNameException(String expected) {
        Assertions.assertThatThrownBy(() -> Teams.createTeamWithTeamName(expected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", expected));
    }
}
