package com.company.officecommute.dto.overtime.response;

public record OverTimeCalculateResponse(
        Long id,
        String name,
        Long overTimeMinutes
) {
}
