-- 공휴일 원장(system of record). 급여 계산은 이 테이블만 읽고, 외부 API는 적재 도구로만 쓴다.
-- V6에서 지운 캐시 테이블(갯수 캐시 + 동기화 상태)의 재도입이 아니라 날짜 단위 원장이다.
-- source: API(재동기화가 갱신·삭제 가능) / MANUAL(관리자 입력, 동기화 불가침)
-- 행이 곧 공휴일이다. 부정 오버라이드(is_holiday류 플래그)는 두지 않는다 —
-- 확정 공휴일의 사후 취소 전례가 없고, API 데이터 오류 대응은 실제 필요 시 도입한다.
CREATE TABLE holiday (
    holiday_date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    source VARCHAR(20) NOT NULL,
    PRIMARY KEY (holiday_date),
    CONSTRAINT ck_holiday_source CHECK (source IN ('API', 'MANUAL'))
);

-- 월 단위 적재 마커: "공휴일 0개인 달"(4월·11월은 정상적으로 0개)과 "아직 적재 안 된 달"을 구분한다.
-- 마커 없는 달은 원장의 완전성을 보장할 수 없으므로 계산을 거부한다. 마커는 동기화 성공만이 세운다.
CREATE TABLE holiday_month_marker (
    marker_month DATE NOT NULL, -- 해당 월의 1일
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (marker_month)
);
