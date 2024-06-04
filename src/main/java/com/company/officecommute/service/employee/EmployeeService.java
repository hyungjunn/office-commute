package com.company.officecommute.service.employee;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveEnrollment;
import com.company.officecommute.domain.annual_leave.AnnualLeaves;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.team.TeamDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeDomainService employeeDomainService;
    private final TeamDomainService teamDomainService;
    private final AnnualLeaveRepository annualLeaveRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            EmployeeDomainService employeeDomainService,
            TeamDomainService teamDomainService,
            AnnualLeaveRepository annualLeaveRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeDomainService = employeeDomainService;
        this.teamDomainService = teamDomainService;
        this.annualLeaveRepository = annualLeaveRepository;
    }

    @Transactional
    public void registerEmployee(EmployeeSaveRequest request) {
        Employee employee = new Employee(request.name(), request.role(), request.birthday(), request.workStartDate());
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

    @Transactional
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(Long employeeId, List<AnnualLeave> wantedLeaves) {
        Employee employee = employeeDomainService.findEmployeeById(employeeId);
        Team team = teamDomainService.findTeamByName(employee.getTeamName());
        List<AnnualLeave> existingAnnualLeaves = annualLeaveRepository.findByEmployeeId(employeeId);

        AnnualLeaveEnrollment enrollment = new AnnualLeaveEnrollment(employeeId, team, existingAnnualLeaves);
        AnnualLeaves annualLeaves = new AnnualLeaves(wantedLeaves);
        enrollment.enroll(annualLeaves);

        List<AnnualLeave> enrolledLeaves = annualLeaveRepository.saveAll(annualLeaves.getAnnualLeaves());
        return new AnnualLeaves(enrolledLeaves).toAnnualLeaveEnrollmentResponse();
    }

}
