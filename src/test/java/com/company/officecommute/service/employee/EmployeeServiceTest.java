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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock
    private PasswordEncoder passwordEncoder;

    private Employee employee;
    private Team team;

    @BeforeEach
    void setUp() {
        employee = new EmployeeBuilder().withId(1L)
                .withName("hyungjunn")
                .withRole(MANAGER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP001")
                .withPassword("password123!")
                .build();

        team = new Team("teamName");
    }

    @Test
    @DisplayName("올바른 사번과 비밀번호로 인증 성공")
    void authenticate_success() {
        String employeeCode = "EMP001";
        String password = "password123!";
        BDDMockito.given(employeeRepository.findByEmployeeCode(employeeCode))
                .willReturn(Optional.of(employee));
        BDDMockito.given(passwordEncoder.matches(password, employee.getPassword()))
                .willReturn(true);

        Employee result = employeeService.authenticate(employeeCode, password);

        assertThat(result).isEqualTo(employee);
    }

    @Test
    @DisplayName("존재하지 않는 사번으로 인증 실패")
    void authenticate_employeeCodeNotFound() {
        BDDMockito.given(employeeRepository.findByEmployeeCode("INVALID_CODE"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.authenticate("INVALID_CODE", "password123!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사번입니다");
    }

    @Test
    @DisplayName("잘못된 비밀번호로 인증 실패")
    void authenticate_wrongPassword() {
        String wrongPassword = "wrongPassword!";
        BDDMockito.given(employeeRepository.findByEmployeeCode("EMP001"))
                .willReturn(Optional.of(employee));
        BDDMockito.given(passwordEncoder.matches(wrongPassword, employee.getPassword()))
                .willReturn(false);

        assertThatThrownBy(() -> employeeService.authenticate("EMP001", wrongPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("직원이 정상적으로 등록된다")
    void registerEmployee_success() {
        EmployeeSaveRequest request = new EmployeeSaveRequest(
                "hyungjunn",
                MANAGER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "password123!"
        );
        BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001"))
                .willReturn(false);
        BDDMockito.given(passwordEncoder.encode("password123!"))
                .willReturn("encodedPassword");

        employeeService.registerEmployee(request);

        verify(employeeRepository).existsByEmployeeCode("EMP001");
        verify(passwordEncoder).encode("password123!");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    @DisplayName("중복된 직원 코드로 등록시 예외가 발생한다")
    void registerEmployee_with_duplicateEmpCode() {
        EmployeeSaveRequest request = new EmployeeSaveRequest(
                "hyungjunn",
                MANAGER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "password123!"
        );
        BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001"))
                .willReturn(true);

        assertThatThrownBy(() -> employeeService.registerEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 직원 코드입니다.");
        verify(employeeRepository).existsByEmployeeCode("EMP001");
        verify(passwordEncoder, BDDMockito.never()).encode(anyString());
        verify(employeeRepository, BDDMockito.never()).save(any(Employee.class));
    }

    @Test
    void testRegisterEmployee() {
        EmployeeSaveRequest request = new EmployeeSaveRequest(
                "hyungjunn",
                MANAGER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "password123!"
        );
        BDDMockito.given(employeeRepository.save(any(Employee.class)))
                .willReturn(employee);
        BDDMockito.given(passwordEncoder.encode("password123!"))
                .willReturn("encodedPassword");

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
