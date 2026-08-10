package com.company.officecommute.service.overtime;

/**
 * 한 직원의 대상 월 초과근무 집계. 가산율이 다른 세 트랙을 분리해 담는다
 * (연장 1.5 / 휴일 8시간 이내 1.5 / 휴일 8시간 초과 2.0 — 근로기준법 제56조).
 */
public record MonthlyOverTime(
        long overTimeMinutes,
        long holidayWithin8HoursMinutes,
        long holidayExceeding8HoursMinutes
) {
}
