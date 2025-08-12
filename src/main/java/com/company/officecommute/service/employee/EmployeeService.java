package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.team.TeamDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final EmployeeDomainService employeeDomainService;
    private final TeamDomainService teamDomainService;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            EmployeeDomainService employeeDomainService,
            TeamDomainService teamDomainService,
            PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.employeeDomainService = employeeDomainService;
        this.teamDomainService = teamDomainService;
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

        employee.changeTeam(team);
    }

    public Employee authenticate(String employeeCode, String password) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사번입니다"));
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        return employee;
    }

}
