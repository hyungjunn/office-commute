package com.company.officecommute.domain.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Entity
public class Employee {

    @Id
    @GeneratedValue
    private Long employeeId;

    private String name;

    private String teamName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private LocalDate birthday;

    private LocalDate workStartDate;

    @Column(unique = true, nullable = false)
    private String employeeCode;

    @Column(nullable = false)
    private String password;

    protected Employee() {
    }

    public Employee(
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate
    ) {
        this(null, name, null, role, birthday, workStartDate, null, null);
    }

    public Employee(
            Long employeeId,
            String name,
            String teamName,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate
    ) {
        this(employeeId, name, teamName, role, birthday, workStartDate, null, null);
    }

    public Employee(
            String name,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate,
            String employeeCode,
            String password
    ) {
        this(null, name, null, role, birthday, workStartDate, employeeCode, password);
    }

    public Employee(
            Long employeeId,
            String name,
            String teamName,
            Role role,
            LocalDate birthday,
            LocalDate workStartDate,
            String employeeCode,
            String password
    ) {
        this.employeeId = employeeId;
        this.name = validateName(name);
        this.teamName = teamName;
        this.role = Objects.requireNonNull(role, "role은 null일 수 없습니다");
        this.birthday = Objects.requireNonNull(birthday, "birthday는 null일 수 없습니다");
        this.workStartDate = Objects.requireNonNull(workStartDate, "workStartDate는 null일 수 없습니다");
        this.employeeCode = validateEmployeeCode(employeeCode);
        this.password = Objects.requireNonNull(password, "password는 null일 수 없습니다");
    }

    private String validateEmployeeCode(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) {
            throw new IllegalArgumentException("employee의 employeeCode가 올바르지 않은 형식입니다.");
        }
        return employeeCode;
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("employee의 name이 올바르지 않은 형식입니다.");
        }
        return name.trim();
    }

    public void changeTeam(String wantedTeamName) {
        this.teamName = wantedTeamName;
    }

    public AnnualLeaves enroll(List<AnnualLeave> wantedLeaves, List<AnnualLeave> existingLeaves) {
        AnnualLeaves annualLeaves = new AnnualLeaves(existingLeaves);
        annualLeaves.enroll(wantedLeaves);
        return annualLeaves;
    }

    public Long getEmployeeId() {
        return employeeId;
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
