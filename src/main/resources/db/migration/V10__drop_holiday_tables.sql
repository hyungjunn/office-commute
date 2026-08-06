-- 공휴일 저장 계층 철회 — 계산은 외부 API 라이브 호출(fail-closed)만 사용한다.
-- 근거와 재도입 조건은 TODO.md 2절 철회 기록 참조. (V8·V9는 적용됨 — 수정 대신 새 V로 삭제)
DROP TABLE IF EXISTS holiday_sync_marker;
DROP TABLE IF EXISTS holiday;
