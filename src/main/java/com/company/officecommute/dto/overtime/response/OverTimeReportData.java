package com.company.officecommute.dto.overtime.response;

public record OverTimeReportData(
        String employeeName,
        String teamName,
        Long overTimeMinutes,
        Long overTimePay
) {
}
