package com.company.officecommute.domain.holiday;

/**
 * 공휴일 원장 행의 출처.
 * <p>
 * API 행은 재동기화가 자유롭게 갱신·삭제할 수 있지만, MANUAL 행은 관리자가 의도적으로
 * 입력한 값이므로 동기화가 건드리지 않는다. 이 구분이 없으면 수동 보정이 다음 동기화에
 * 조용히 지워진다.
 */
public enum HolidaySource {
    API, MANUAL
}
