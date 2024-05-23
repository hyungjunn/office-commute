package com.company.officecommute.repository.employee;

import com.company.officecommute.domain.employee.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @Query("""
            SELECT new Employee (e.name, e.role, e.birthday, e.workStartDate)
            FROM Employee e
            """)
    List<Employee> findEmployeeHierarchy();
}
