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
    private Long id;

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

    public Team(Long id, String name, String managerName, int memberCount) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(String.format("(%s)는 공백입니다. 팀명을 정확하게 입력해주세요.", name));
        }
        this.id = id;
        this.name = name;
        this.managerName = managerName;
        this.memberCount = memberCount;
    }

    public Long getId() {
        return this.id;
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

    public Optional<TeamFindResponse> toResponse() {
        return Optional.of(new TeamFindResponse(this.name, this.managerName, this.memberCount));
    }
}
