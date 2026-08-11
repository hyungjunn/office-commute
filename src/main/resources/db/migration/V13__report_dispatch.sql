-- 매월 초과근무 리포트 발송 이력.
-- UNIQUE(target_year_month) 가 중복 발송 방지의 유일한 하드 보증이다 —
-- 스케줄 간격·상태 전이는 전부 그 위의 편의 계층이고, 배치 재시도와 수동 재실행이
-- 겹쳐도 대표는 한 달에 한 통만 받는다.
--
-- target_year_month 를 DATE 로 두지 않는 이유: '1일'이라는 존재하지 않는 날짜가
-- 의미를 갖는 것처럼 보인다. 'yyyy-MM' 문자열이 대상 월이라는 사실을 그대로 표현한다.
CREATE TABLE report_dispatch
(
    report_dispatch_id  BIGINT       NOT NULL AUTO_INCREMENT,
    target_year_month   CHAR(7)      NOT NULL, -- 'yyyy-MM'
    status              VARCHAR(20)  NOT NULL, -- IN_PROGRESS | SENT | FAILED
    attempt_count       INT          NOT NULL,
    last_attempted_at   DATETIME(6)  NOT NULL,
    sent_at             DATETIME(6),
    last_failure_reason VARCHAR(1000),
    PRIMARY KEY (report_dispatch_id),
    CONSTRAINT uk_report_dispatch_year_month UNIQUE (target_year_month)
);
