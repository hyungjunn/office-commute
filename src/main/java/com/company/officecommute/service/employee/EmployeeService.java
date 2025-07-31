package com.company.officecommute.service.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveEnrollment;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeDomainService employeeDomainService;
    private final TeamDomainService teamDomainService;
    private final AnnualLeaveRepository annualLeaveRepository;
    private final CommuteHistoryRepository commuteHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            EmployeeDomainService employeeDomainService,
            TeamDomainService teamDomainService,
            AnnualLeaveRepository annualLeaveRepository,
            CommuteHistoryRepository commuteHistoryRepository,
            PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.employeeDomainService = employeeDomainService;
        this.teamDomainService = teamDomainService;
        this.annualLeaveRepository = annualLeaveRepository;
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void registerEmployee(EmployeeSaveRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new IllegalArgumentException("이미 존재하는 직원 코드입니다.");
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        Employee employee = new Employee(
                request.name(),
                request.role(),
                request.birthday(),
                request.workStartDate(),
                request.employeeCode(),
                encodedPassword
        );
        employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeRepository.findEmployeeHierarchy()
                .stream()
                .map(EmployeeFindResponse::from)
                .toList();
    }

    @Transactional
    public void updateEmployeeTeamName(EmployeeUpdateTeamNameRequest request) {
        Employee employee = employeeDomainService.findEmployeeById(request.employeeId());

        String wantedTeamName = request.teamName();
        Team team = teamDomainService.findTeamByName(wantedTeamName);

        employee.changeTeam(wantedTeamName);
        team.increaseMemberCount();
    }

    public Employee authenticate(String employeeCode, String password) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사번입니다"));
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        return employee;
    }

    @Transactional
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(Long employeeId, List<LocalDate> wantedDates) {
        // TODO: 추후 리팩터링 고려
        List<AnnualLeave> wantedLeaves = wantedDates.stream()
                .map(wantedDate -> new AnnualLeave(employeeId, wantedDate))
                .toList();
        Employee employee = employeeDomainService.findEmployeeById(employeeId);
        Team team = teamDomainService.findTeamByName(employee.getTeamName());
        List<AnnualLeave> existingAnnualLeaves = annualLeaveRepository.findByEmployeeId(employeeId);

        AnnualLeaveEnrollment enrollment = new AnnualLeaveEnrollment(employeeId, team, existingAnnualLeaves);
        AnnualLeaves annualLeaves = new AnnualLeaves(wantedLeaves);
        enrollment.enroll(annualLeaves);

        List<AnnualLeave> enrolledLeaves = annualLeaveRepository.saveAll(annualLeaves.getAnnualLeaves());

        // 연차에 대응되는 근무이력 객체로 변환
        enrolledLeaves.stream()
                .map(annualLeave -> new CommuteHistory(employeeId))
                .forEach(commuteHistoryRepository::save);

        return new AnnualLeaves(enrolledLeaves).toAnnualLeaveEnrollmentResponse();
    }

    @Transactional(readOnly = true)
    public AnnualLeaveGetRemainingResponse getRemainingAnnualLeaves(Long employeeId) {
        List<AnnualLeave> remainingLeaves = annualLeaveRepository.findByEmployeeId(employeeId)
                .stream()
                .filter(AnnualLeave::isRemain)
                .toList();

        return new AnnualLeaveGetRemainingResponse(employeeId, remainingLeaves);
    }
}
