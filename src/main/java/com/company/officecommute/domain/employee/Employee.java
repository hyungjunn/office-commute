package com.company.officecommute.domain.employee;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class Employee {

    @Id @GeneratedValue
    private Long id;

    private String name;

    private String teamName;

    private Role role;

    private LocalDate birthday;
    
    private LocalDate workStartDate;
}
