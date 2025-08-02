package com.company.officecommute.repository.employee;

import com.company.officecommute.domain.employee.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @Query("""
            SELECT new Employee (e.name, e.role, e.birthday, e.workStartDate)
            FROM Employee e
            """)
    List<Employee> findEmployeeHierarchy();

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);

    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            WHERE e.employeeId = :employeeId
            """)
    Optional<Employee> findByEmployeeIdWithTeam(@Param("employeeId") Long employeeId);
}
