package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.global.exception.CustomException;
import com.company.officecommute.global.exception.ErrorCode;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Service;

@Service
public class EmployeeDomainService {

    private final EmployeeRepository employeeRepository;

    public EmployeeDomainService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee findEmployeeById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }
}
