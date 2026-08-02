-- V8이 "부정 오버라이드(is_holiday류 플래그)는 두지 않는다 / 행이 곧 공휴일이다"라고 적어 둔 결정을
-- 여기서 뒤집는다. V8은 적용된 파일이라 수정할 수 없으므로, 그 주석은 V9 이후로는 유효하지 않다.
--
-- 뒤집는 이유:
--  1. 관리자 보정이 "추가"만 가능하면 API가 잘못 준 날(또는 회사가 정상 근무로 돌린 날)을 되돌릴 방법이 없다.
--     is_holiday=false 행이 그 되돌림을 표현한다. 부정 오버라이드는 MANUAL 전용이다 —
--     API 응답과 회사 지정 휴일은 정의상 휴일이므로 false를 가질 수 없다.
--  2. 회사 지정 휴일(창립기념일 등)은 법정 공휴일도 관리자 보정도 아니다. 출처가 섞이면
--     "동기화가 지워도 되는 행"의 판단이 무너지므로 COMPANY를 별도 출처로 둔다.
--  3. 같은 날짜에 여러 출처의 행이 공존해야 한다(예: API 휴일 + MANUAL 부정 오버라이드).
--     따라서 PK를 holiday_date 단독에서 (holiday_date, source) 복합키로 넓힌다.
--     날짜별 최종 판정은 계산 경로의 판정 규칙이 내린다 —
--     MANUAL이 is_holiday=false를 걸었으면 근무일, 아니면 is_holiday=true 행이 하나라도 있으면 휴일.

ALTER TABLE holiday
    ADD COLUMN is_holiday BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE holiday
    DROP CHECK ck_holiday_source;

ALTER TABLE holiday
    ADD CONSTRAINT ck_holiday_source CHECK (source IN ('API', 'MANUAL', 'COMPANY'));

-- 부정 오버라이드는 MANUAL 전용. 도메인 생성자와 같은 규칙을 DDL에도 건다.
ALTER TABLE holiday
    ADD CONSTRAINT ck_holiday_negative_override_is_manual CHECK (is_holiday = TRUE OR source = 'MANUAL');

ALTER TABLE holiday
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (holiday_date, source);
