package com.company.officecommute.web;

import java.time.LocalDate;

/**
 * 공휴일 API에서 받아 검증을 통과한 항목. getRestDeInfo 응답 항목은 정의상 모두 공휴일이다.
 */
public record HolidayApiItem(LocalDate date, String name) {
}
