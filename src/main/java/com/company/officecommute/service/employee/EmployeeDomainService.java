package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
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
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("해당하는 직원(%s)이 없습니다.", employeeId)
                ));
    }
}
