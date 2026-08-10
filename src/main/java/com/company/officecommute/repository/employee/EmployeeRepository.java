package com.company.officecommute.repository.employee;

import com.company.officecommute.domain.employee.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            """)
    List<Employee> findAllWithTeam();

    /**
     * 재직 기간이 [rangeStart, rangeEnd]와 겹치는 직원.
     * 기간 중 퇴사자는 포함(그 기간의 초과근무는 지급 대상), 기간 시작 전 퇴사자·기간 종료 후 입사자는 제외.
     */
    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            WHERE (e.workEndDate IS NULL OR e.workEndDate >= :rangeStart)
              AND e.workStartDate <= :rangeEnd
            """)
    List<Employee> findAllWithTeamEmployedBetween(
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            SELECT e
            FROM Employee e
            LEFT JOIN FETCH e.team
            WHERE e.employeeId = :employeeId
            """)
    Optional<Employee> findByEmployeeIdWithTeam(@Param("employeeId") Long employeeId);

    @Query("""
            SELECT e.team.teamId, COUNT(e)
            FROM Employee e
            WHERE e.team.teamId IN :teamIds
            GROUP BY e.team.teamId
            """)
    List<Object[]> countMembersByTeamIdsRaw(@Param("teamIds") List<Long> teamIds);
}
