package com.company.officecommute.controller.employee;

import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.service.employee.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/employee")
    public void saveEmployee(@RequestBody EmployeeSaveRequest request) {
        employeeService.registerEmployee(request);
    }

    @GetMapping("/employee")
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeService.findAllEmployee();
    }
}
