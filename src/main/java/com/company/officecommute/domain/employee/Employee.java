package com.company.officecommute.domain.employee;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class Employee {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String teamName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDate birthday;

    private LocalDate workStartDate;

    protected Employee() {
    }

    public Employee(
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate
    ) {
        this(null, name, null, role, birthday, workStartDate);
    }

    public Employee(
            Long id,
            String name,
            String teamName,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate
    ) {
        validateEmployeeParameters(name, role, birthday, workStartDate);
        this.id = id;
        this.name = name;
        this.teamName = teamName;
        this.role = role;
        this.birthday = birthday;
        this.workStartDate = workStartDate;
    }

    private void validateEmployeeParameters(String name, Role role, LocalDate birthday, LocalDate workStartDate) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(String.format("employee의 name(%s)이 올바르지 않은 형식입니다. 다시 입력해주세요.", name));
        }

        if (role == null) {
            throw new IllegalArgumentException(String.format("employee의 role이 올바르지 않은 형식(%s)입니다. 다시 입력해주세요.", role));
        }

        if (birthday == null) {
            throw new IllegalArgumentException(String.format("employee의 birthday이 올바르지 않은 형식(%s)입니다. 다시 입력해주세요.", null));
        }

        if (workStartDate == null) {
            throw new IllegalArgumentException(String.format("employee의 workStartDate이 올바르지 않은 형식(%s)입니다. 다시 입력해주세요.", null));
        }
    }

    public void changeTeam(String wantedTeamName) {
        this.teamName = wantedTeamName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeamName() {
        return teamName;
    }

    public Role getRole() {
        return role;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public LocalDate getWorkStartDate() {
        return workStartDate;
    }
}
