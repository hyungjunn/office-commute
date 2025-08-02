package com.company.officecommute.service.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
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
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
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

    private Long employeeId;
    private Employee employee;
    private Team team;

    @BeforeEach
    void setUp() {
        employeeId = 1L;
        team = new Team("백엔드팀");
        employee = new EmployeeBuilder()
                .withId(employeeId)
                .withTeam(team)
                .withName("임형준")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP001")
                .withPassword("password123!")
                .build();

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
                "임형준",
                MEMBER,
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
                "임형준",
                MEMBER,
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
                "임형준",
                MEMBER,
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
        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "백엔드팀");
        BDDMockito.given(employeeDomainService.findEmployeeById(1L))
                .willReturn(employee);

        BDDMockito.given(teamDomainService.findTeamByName(anyString()))
                .willReturn(team);

        employeeService.updateEmployeeTeamName(request);

        assertThat(employee.getTeamName()).isEqualTo("백엔드팀");
        assertThat(team.getMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연차 신청이 정상적으로 처리된다")
    void testEnrollAnnualLeave() {
        List<LocalDate> wantedDates = List.of(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(11)
        );
        BDDMockito.given(employeeRepository.findByEmployeeIdWithTeam(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(annualLeaveRepository.findByEmployeeId(employeeId))
                .willReturn(List.of());
        List<AnnualLeave> savedLeaves = List.of(
                new AnnualLeave(1L, employeeId, wantedDates.get(0)),
                new AnnualLeave(2L, employeeId, wantedDates.get(1))
        );
        BDDMockito.given(annualLeaveRepository.saveAll(any()))
                .willReturn(savedLeaves);
        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(new CommuteHistory(employeeId));

        List<AnnualLeaveEnrollmentResponse> responses = employeeService.enrollAnnualLeave(employeeId, wantedDates);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).annualLeaveId()).isEqualTo(1L);
        assertThat(responses.get(0).enrolledDate()).isEqualTo(wantedDates.get(0));
        verify(commuteHistoryRepository, times(2)).save(any(CommuteHistory.class));
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
