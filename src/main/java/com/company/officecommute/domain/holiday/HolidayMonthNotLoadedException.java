package com.company.officecommute.domain.holiday;

import java.time.YearMonth;

/**
 * 해당 월의 공휴일 원장이 아직 적재되지 않아 계산을 거부할 때 던진다.
 * 빈 원장을 "공휴일 0개"로 해석하면 소정근로일 과대 → 초과근무 과소 집계로 이어지므로
 * 마커 없는 달은 값 대신 이 예외를 반환한다.
 */
public class HolidayMonthNotLoadedException extends RuntimeException {

    public HolidayMonthNotLoadedException(YearMonth month) {
        super("해당 월의 공휴일이 아직 적재되지 않아 계산할 수 없습니다. 공휴일 동기화를 먼저 실행해 주세요. month=" + month);
    }
}
