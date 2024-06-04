package com.company.officecommute.controller.annual_leave;

import com.company.officecommute.dto.annual_leave.request.AnnualLeaveEnrollRequest;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.service.employee.EmployeeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AnnualLeaveController {

    private final EmployeeService employeeService;

    public AnnualLeaveController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/annual-leave")
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(@RequestBody AnnualLeaveEnrollRequest request) {
        return employeeService.enrollAnnualLeave(request.employeeId(), request.datesToAnnualLeaves());
    }

}
