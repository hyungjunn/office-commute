package com.company.officecommute.domain.employee;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class Employee {

    @Id @GeneratedValue
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
        this.id = id;
        this.name = name;
        this.teamName = teamName;
        this.role = role;
        this.birthday = birthday;
        this.workStartDate = workStartDate;
    }
}
