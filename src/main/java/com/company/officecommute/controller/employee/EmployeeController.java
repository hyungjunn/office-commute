package com.company.officecommute.controller.employee;

import com.company.officecommute.auth.AuthUtils;
import com.company.officecommute.auth.RequireRole;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.auth.LoginRequest;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.service.employee.EmployeeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request,
                                        HttpSession session) {
        Employee employee = employeeService.authenticate(request.employeeCode(), request.password());
        session.setAttribute("employeeId", employee.getEmployeeId());
        session.setAttribute("employeeRole", employee.getRole());
        return ResponseEntity.ok("로그인 성공");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("로그아웃 성공");
    }

    @RequireRole({Role.MANAGER})
    @PostMapping("/employee")
    public void saveEmployee(@Valid @RequestBody EmployeeSaveRequest request,
                             HttpServletRequest httpRequest) {
        AuthUtils.requireManagerRole(httpRequest);
        employeeService.registerEmployee(request);
    }

    @RequireRole({Role.MANAGER})
    @GetMapping("/employee")
    public List<EmployeeFindResponse> findAllEmployee(HttpServletRequest httpRequest) {
        AuthUtils.requireManagerRole(httpRequest);
        return employeeService.findAllEmployee();
    }

    @RequireRole({Role.MANAGER})
    @PutMapping("/employee")
    public void updateEmployeeTeamName(@RequestBody EmployeeUpdateTeamNameRequest request,
                                       HttpServletRequest httpRequest) {
        AuthUtils.requireManagerRole(httpRequest);
        employeeService.updateEmployeeTeamName(request);
    }
}
