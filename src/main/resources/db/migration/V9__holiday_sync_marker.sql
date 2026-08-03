-- 공휴일 동기화 마커. 단위는 연(年)이다 — 동기화도 "미발표" 판정도 연 단위이므로
-- 월 마커 12개는 같은 사실의 비정규화일 뿐이다.
-- 마커 있음 + 해당 월 행 없음 = 정상 0개(4월·11월), 마커 없음 = 미적재(계산 거부).
CREATE TABLE holiday_sync_marker (
    sync_year INT      NOT NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (sync_year)
);
