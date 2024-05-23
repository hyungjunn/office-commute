package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;

import java.time.LocalDate;

public class EmployeeBuilder {
    private Long id;
    private String name;
    private String teamName;
    private Role role;
    private LocalDate birthday;
    private LocalDate workStartDate;

    public EmployeeBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public EmployeeBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public EmployeeBuilder withRole(Role role) {
        this.role = role;
        return this;
    }

    public EmployeeBuilder withBirthday(LocalDate date) {
        this.birthday = date;
        return this;
    }

    public EmployeeBuilder withStartDate(LocalDate date) {
        this.workStartDate = date;
        return this;
    }

    public Employee build() {
        return new Employee(id, name, teamName, role, birthday, workStartDate);
    }
}
