package com.company.officecommute.controller.employee;

import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.service.employee.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.company.officecommute.web.ApiUrlConstant.EMPLOYEE;

@RestController
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping(EMPLOYEE)
    public void saveEmployee(@RequestBody EmployeeSaveRequest request) {
        employeeService.registerEmployee(request);
    }

    @GetMapping(EMPLOYEE)
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeService.findAllEmployee();
    }

    @PutMapping(EMPLOYEE)
    public void updateEmployeeTeamName(@RequestBody EmployeeUpdateTeamNameRequest request) {
        employeeService.updateEmployeeTeamName(request);
    }
}
