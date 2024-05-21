package com.company.officecommute.domain.team;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Team {

    @Id @GeneratedValue
    private Long id;

    private String name;

    private String managerName;

    private int memberCount;

    protected Team() {
    }

    public Team(String name) {
        this(null, name, null, 0);
    }

    public Team(Long id, String name, String managerName, int memberCount) {
        this.id = id;
        this.name = name;
        this.managerName = managerName;
        this.memberCount = memberCount;
    }

    public Long getId() {
        return this.id;
    }
}
