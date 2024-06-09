package com.company.officecommute.service.overtime;

public class TotalWorkingMinutes {
    private final Long employeeId;
    private final String employeeName;
    private final Long totalWorkingMinutes;

    public TotalWorkingMinutes(Long employeeId, String employeeName, Long totalWorkingMinutes) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.totalWorkingMinutes = totalWorkingMinutes;
    }

    public long calculateOverTime(long standardWorkingMinutes) {
        if (totalWorkingMinutes > standardWorkingMinutes) {
            return totalWorkingMinutes - standardWorkingMinutes;
        }
        return 0;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }
}
