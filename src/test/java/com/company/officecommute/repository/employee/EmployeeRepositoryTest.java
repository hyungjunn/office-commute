package com.company.officecommute.repository.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EmployeeRepositoryTest {

    private static final LocalDate MONTH_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate MONTH_END = LocalDate.of(2026, 8, 31);

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void clearSeededEmployees() {
        // 임베디드 H2에서는 data.sql 시드(ADMIN001)가 자동 로드되므로 비우고 시작한다
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("findAllWithTeamEmployedBetween — 퇴사일 없는 재직자는 포함한다")
    void includesEmployeeWithoutWorkEndDate() {
        employeeRepository.save(employee("EMP001", LocalDate.of(2024, 1, 1), null));

        List<Employee> result = employeeRepository.findAllWithTeamEmployedBetween(MONTH_START, MONTH_END);

        assertThat(result).extracting(Employee::getEmployeeCode).containsExactly("EMP001");
    }

    @Test
    @DisplayName("findAllWithTeamEmployedBetween — 월 1일 퇴사자는 포함한다 (경계: 그 하루의 근무도 지급 대상)")
    void includesEmployeeRetiredOnMonthStart() {
        employeeRepository.save(employee("EMP001", LocalDate.of(2024, 1, 1), MONTH_START));

        List<Employee> result = employeeRepository.findAllWithTeamEmployedBetween(MONTH_START, MONTH_END);

        assertThat(result).extracting(Employee::getEmployeeCode).containsExactly("EMP001");
    }

    @Test
    @DisplayName("findAllWithTeamEmployedBetween — 월 시작 전날 퇴사자는 제외한다")
    void excludesEmployeeRetiredBeforeMonthStart() {
        employeeRepository.save(employee("EMP001", LocalDate.of(2024, 1, 1), MONTH_START.minusDays(1)));

        List<Employee> result = employeeRepository.findAllWithTeamEmployedBetween(MONTH_START, MONTH_END);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAllWithTeamEmployedBetween — 월 종료 후 입사 예정자는 제외한다")
    void excludesEmployeeStartingAfterMonthEnd() {
        employeeRepository.save(employee("EMP001", MONTH_END.plusDays(1), null));

        List<Employee> result = employeeRepository.findAllWithTeamEmployedBetween(MONTH_START, MONTH_END);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAllWithTeamEmployedBetween — 월 중 퇴사자는 포함한다 (그 달 초과근무는 지급 대상)")
    void includesMidMonthRetiree() {
        employeeRepository.save(employee("EMP001", LocalDate.of(2024, 1, 1), LocalDate.of(2026, 8, 15)));

        List<Employee> result = employeeRepository.findAllWithTeamEmployedBetween(MONTH_START, MONTH_END);

        assertThat(result).extracting(Employee::getEmployeeCode).containsExactly("EMP001");
    }

    private Employee employee(String employeeCode, LocalDate workStartDate, LocalDate workEndDate) {
        Employee employee = Employee.register(
                "임형준",
                Role.MEMBER,
                LocalDate.of(1998, 8, 18),
                workStartDate,
                employeeCode,
                employeeCode.toLowerCase() + "@company.com",
                "password123",
                null,
                null
        );
        employee.changeWorkEndDate(workEndDate);
        return employee;
    }
}
