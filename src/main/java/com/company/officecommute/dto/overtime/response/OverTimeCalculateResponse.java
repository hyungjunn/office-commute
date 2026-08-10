package com.company.officecommute.dto.overtime.response;

public record OverTimeCalculateResponse(
        Long id,
        String employeeCode,
        String name,
        String teamName,
        long overTimeMinutes,
        long holidayWithin8HoursMinutes,
        long holidayExceeding8HoursMinutes
) {
}
