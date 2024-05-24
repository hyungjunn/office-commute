package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @InjectMocks
    private EmployeeService employeeService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TeamRepository teamRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new EmployeeBuilder().withId(1L)
                .withName("hyungjunn")
                .withRole(MANAGER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .build();
    }

    @Test
    void testRegisterEmployee() {
        EmployeeSaveRequest request = new EmployeeSaveRequest("hyungjunn", MANAGER, LocalDate.of(1998, 8, 18), LocalDate.of(2024, 1, 1));
        BDDMockito.given(employeeRepository.save(any(Employee.class)))
                .willReturn(employee);

        employeeService.registerEmployee(request);

        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void testFindAllEmployee() {
        BDDMockito.given(employeeRepository.findEmployeeHierarchy())
                .willReturn(List.of(employee));

        List<EmployeeFindResponse> employees = employeeService.findAllEmployee();

        assertThat(employees).hasSize(1);
        assertThat(employees.contains(EmployeeFindResponse.from(employee))).isTrue();
    }

    @Test
    void testUpdateEmployeeTeamName() {
        Team team = new Team("teamName");

        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "teamName");
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));

        BDDMockito.given(teamRepository.findByName(anyString()))
                .willReturn(team);

        employeeService.updateEmployeeTeamName(request);

        assertThat(employee.getTeamName()).isEqualTo("teamName");
        assertThat(team.getMemberCount()).isEqualTo(1);
    }
}
