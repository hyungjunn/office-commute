package com.company.officecommute.domain.team;

import com.company.officecommute.dto.team.response.TeamFindResponse;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.Optional;

@Entity
public class Team {

    @Id
    @GeneratedValue
    private Long teamId;

    private String name;

    private String managerName;

    private int memberCount;

    protected Team() {
    }

    public Team(String name) {
        this(null, name, null, 0);
    }

    public Team(String name, String managerName, int memberCount) {
        this(null, name, managerName, memberCount);
    }

    public Team(Long teamId, String name, String managerName, int memberCount) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", name));
        }
        this.teamId = teamId;
        this.name = name;
        this.managerName = managerName;
        this.memberCount = memberCount;
    }

    public Long getTeamId() {
        return this.teamId;
    }

    public String getName() {
        return this.name;
    }

    public String getManagerName() {
        return this.managerName;
    }

    public int getMemberCount() {
        return this.memberCount;
    }

    public void increaseMemberCount() {
        this.memberCount++;
    }
}
