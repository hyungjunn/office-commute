package com.company.officecommute.service.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.team.TeamDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private EmployeeDomainService employeeDomainService;

    @Mock
    private TeamDomainService teamDomainService;

    @Mock
    private AnnualLeaveRepository annualLeaveRepository;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    private Employee employee;
    private Team team;

    @BeforeEach
    void setUp() {
        employee = new EmployeeBuilder().withId(1L)
                .withName("hyungjunn")
                .withRole(MANAGER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .build();

        team = new Team("teamName");
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
        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "teamName");
        BDDMockito.given(employeeDomainService.findEmployeeById(1L))
                .willReturn(employee);

        BDDMockito.given(teamDomainService.findTeamByName(anyString()))
                .willReturn(team);

        employeeService.updateEmployeeTeamName(request);

        assertThat(employee.getTeamName()).isEqualTo("teamName");
        assertThat(team.getMemberCount()).isEqualTo(1);
    }

    @Test
    void testEnrollAnnualLeave() {
        // given
        ArrayList<AnnualLeave> wantedLeaves = new ArrayList<>(List.of(new AnnualLeave(1L, 1L, LocalDate.now().plusDays(20))));

        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "teamName");
        BDDMockito.given(employeeDomainService.findEmployeeById(1L))
                .willReturn(employee);

        BDDMockito.given(teamDomainService.findTeamByName(anyString()))
                .willReturn(team);

        AnnualLeaves annualLeaves = new AnnualLeaves(wantedLeaves);
        BDDMockito.given(annualLeaveRepository.saveAll(annualLeaves.getAnnualLeaves()))
                .willReturn(wantedLeaves);

        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(new CommuteHistory(1L, 1L, ZonedDateTime.now(), null, 0));

        employeeService.updateEmployeeTeamName(request);

        // when
        List<AnnualLeaveEnrollmentResponse> responses = employeeService.enrollAnnualLeave(1L, wantedLeaves);

        // then
        verify(annualLeaveRepository).saveAll(annualLeaves.getAnnualLeaves());
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).annualLeaveId()).isEqualTo(1L);
        assertThat(responses.get(0).enrolledDate()).isEqualTo(LocalDate.now().plusDays(20));
    }

    @Test
    void testGetRemainingAnnualLeave() {
        BDDMockito.given(annualLeaveRepository.findByEmployeeId(1L))
                .willReturn(List.of(new AnnualLeave(1L, 1L, LocalDate.now().plusDays(20))));

        AnnualLeaveGetRemainingResponse response = employeeService.getRemainingAnnualLeaves(1L);

        verify(annualLeaveRepository).findByEmployeeId(1L);
        assertThat(response.employeeId()).isEqualTo(1L);
    }
}
