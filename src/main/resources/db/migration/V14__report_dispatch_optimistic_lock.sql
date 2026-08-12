-- FAILED 재시도와 만료된 IN_PROGRESS 리스 회수가 동시에 일어날 때 한 실행만
-- 선점하도록 낙관적 락 버전을 둔다.
ALTER TABLE report_dispatch
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER report_dispatch_id;
