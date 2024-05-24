package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;

    public EmployeeService(EmployeeRepository employeeRepository, TeamRepository teamRepository) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
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
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new IllegalArgumentException(String.format("해당하는 직원(%s)이 없습니다.", request.employeeId())));

        String wantedTeamName = request.teamName();
        Team team = teamRepository.findByName(wantedTeamName);
        if (team == null) {
            throw new IllegalArgumentException(String.format("해당하는 팀(%s)이 없습니다.", wantedTeamName));
        }
        employee.changeTeam(wantedTeamName);
        team.increaseMemberCount();
    }
}
