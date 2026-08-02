DELETE FROM employee WHERE employee_code = 'ADMIN001';
DELETE FROM team WHERE name = '관리팀';

-- 관리자 비밀번호(평문): admin1234 (BCryptPasswordEncoder strength=10)
INSERT INTO employee (name, role, birthday, work_start_date, employee_code, email, password, timezone)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001', 'admin@company.com',
        '$2a$10$jg1.5WoxGYRAvXMnQbjuzO00fqODW80lysuhA0an2vD/VqgHY6MDm', 'Asia/Seoul');

INSERT INTO team (name, manager_name, annual_leave_criteria)
VALUES ('관리팀', '관리자', 15);

-- 2026년 공휴일 개발용 시드.
-- 계산 경로는 원장만 읽고, 마커 없는 달은 계산을 거부한다. 시드가 없으면 dev 리포트가 항상
-- 503(HOLIDAY_MONTH_NOT_LOADED)으로 막혀 화면을 볼 수조차 없다.
--
-- **이 값은 개발 편의용 근사치다.** 정확한 원장은 `POST /holiday/sync?year=...` 또는 매일 새벽
-- 정기 동기화가 공공데이터포털에서 채운다. 2026년만 시드하므로 다른 해는 동기화로 채워야 하고,
-- 그때 이 행들은 연 단위 범위 교체로 API 값에 밀려난다.
DELETE FROM holiday;
DELETE FROM holiday_month_marker;

INSERT INTO holiday (holiday_date, name, source, is_holiday) VALUES
    ('2026-01-01', '1월1일', 'API', TRUE),
    ('2026-02-16', '설날', 'API', TRUE),
    ('2026-02-17', '설날', 'API', TRUE),
    ('2026-02-18', '설날', 'API', TRUE),
    ('2026-03-01', '삼일절', 'API', TRUE),
    ('2026-03-02', '대체공휴일', 'API', TRUE),
    ('2026-05-05', '어린이날', 'API', TRUE),
    ('2026-05-24', '부처님오신날', 'API', TRUE),
    ('2026-05-25', '대체공휴일', 'API', TRUE),
    ('2026-06-03', '지방선거일', 'API', TRUE),
    ('2026-06-06', '현충일', 'API', TRUE),
    ('2026-07-17', '제헌절', 'API', TRUE),
    ('2026-08-15', '광복절', 'API', TRUE),
    ('2026-08-17', '대체공휴일', 'API', TRUE),
    ('2026-09-24', '추석', 'API', TRUE),
    ('2026-09-25', '추석', 'API', TRUE),
    ('2026-09-26', '추석', 'API', TRUE),
    ('2026-09-28', '대체공휴일', 'API', TRUE),
    ('2026-10-03', '개천절', 'API', TRUE),
    ('2026-10-05', '대체공휴일', 'API', TRUE),
    ('2026-10-09', '한글날', 'API', TRUE),
    ('2026-12-25', '기독탄신일', 'API', TRUE);

-- 공휴일이 0건인 4월·11월도 마커를 세운다 — 마커가 "정상적으로 0개인 달"과 "미적재"를 가른다.
INSERT INTO holiday_month_marker (marker_month, synced_at) VALUES
    ('2026-01-01', CURRENT_TIMESTAMP),
    ('2026-02-01', CURRENT_TIMESTAMP),
    ('2026-03-01', CURRENT_TIMESTAMP),
    ('2026-04-01', CURRENT_TIMESTAMP),
    ('2026-05-01', CURRENT_TIMESTAMP),
    ('2026-06-01', CURRENT_TIMESTAMP),
    ('2026-07-01', CURRENT_TIMESTAMP),
    ('2026-08-01', CURRENT_TIMESTAMP),
    ('2026-09-01', CURRENT_TIMESTAMP),
    ('2026-10-01', CURRENT_TIMESTAMP),
    ('2026-11-01', CURRENT_TIMESTAMP),
    ('2026-12-01', CURRENT_TIMESTAMP);
