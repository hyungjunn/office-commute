-- 퇴사일. NULL = 재직 중.
-- 리포트 대상 판정: (work_end_date IS NULL OR work_end_date >= 대상월 1일) AND work_start_date <= 대상월 말일
ALTER TABLE employee
    ADD COLUMN work_end_date DATE NULL;
