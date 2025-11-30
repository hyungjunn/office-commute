package com.company.officecommute.domain.overtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.util.Objects;

@Entity
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_value", nullable = false)
    private int year;

    @Column(name = "month_value", nullable = false)
    private int month;

    @Column(nullable = false)
    private LocalDate holidayDate;

    protected Holiday() {
    }

    public Holiday(int year, int month, LocalDate holidayDate) {
        this.year = year;
        this.month = month;
        this.holidayDate = holidayDate;
    }

    public Long getId() {
        return id;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Holiday holiday = (Holiday) o;
        return Objects.equals(id, holiday.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
