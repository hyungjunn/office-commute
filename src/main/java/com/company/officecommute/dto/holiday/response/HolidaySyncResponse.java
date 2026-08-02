package com.company.officecommute.dto.holiday.response;

/**
 * 동기화 트리거의 결과. 적재 건수를 돌려주는 이유는 "성공했다"만으로는 관리자가 원장이 실제로
 * 채워졌는지 알 수 없기 때문이다.
 */
public record HolidaySyncResponse(int year, int holidayCount) {
}
