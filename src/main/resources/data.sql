DELETE FROM employee WHERE employee_code = 'ADMIN001';
DELETE FROM team WHERE name = '관리팀';

-- 관리자 비밀번호(평문): admin1234 (BCryptPasswordEncoder strength=10)
INSERT INTO employee (name, role, birthday, work_start_date, employee_code, email, password, timezone)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001', 'admin@company.com',
        '$2a$10$jg1.5WoxGYRAvXMnQbjuzO00fqODW80lysuhA0an2vD/VqgHY6MDm', 'Asia/Seoul');

INSERT INTO team (name, manager_name, annual_leave_criteria)
VALUES ('관리팀', '관리자', 15);

-- 공휴일 원장 dev 시드. 마커가 없으면 dev에서 모든 월이 "미적재"로 거부되므로 반드시 함께 넣는다.
-- 데모용 근사 데이터다 — 실제 판정 근거는 동기화가 적재한 값이고, 여기 날짜를 정본으로 삼지 않는다.
DELETE FROM holiday_sync_marker;
DELETE FROM holiday;

INSERT INTO holiday (holiday_date, name, source, is_holiday) VALUES
('2025-01-01', '1월1일', 'API', TRUE),
('2025-01-28', '설날', 'API', TRUE),
('2025-01-29', '설날', 'API', TRUE),
('2025-01-30', '설날', 'API', TRUE),
('2025-03-01', '삼일절', 'API', TRUE),
('2025-03-03', '대체공휴일', 'API', TRUE),
('2025-05-05', '어린이날', 'API', TRUE),
('2025-05-06', '대체공휴일', 'API', TRUE),
('2025-06-06', '현충일', 'API', TRUE),
('2025-08-15', '광복절', 'API', TRUE),
('2025-10-03', '개천절', 'API', TRUE),
('2025-10-05', '추석', 'API', TRUE),
('2025-10-06', '추석', 'API', TRUE),
('2025-10-07', '추석', 'API', TRUE),
('2025-10-08', '대체공휴일', 'API', TRUE),
('2025-10-09', '한글날', 'API', TRUE),
('2025-12-25', '기독탄신일', 'API', TRUE),
('2026-01-01', '1월1일', 'API', TRUE),
('2026-02-16', '설날', 'API', TRUE),
('2026-02-17', '설날', 'API', TRUE),
('2026-02-18', '설날', 'API', TRUE),
('2026-03-01', '삼일절', 'API', TRUE),
('2026-03-02', '대체공휴일', 'API', TRUE),
('2026-05-05', '어린이날', 'API', TRUE),
('2026-05-24', '부처님오신날', 'API', TRUE),
('2026-05-25', '대체공휴일', 'API', TRUE),
('2026-06-06', '현충일', 'API', TRUE),
('2026-07-17', '제헌절', 'API', TRUE),
('2026-08-15', '광복절', 'API', TRUE),
('2026-08-17', '대체공휴일', 'API', TRUE),
('2026-09-24', '추석', 'API', TRUE),
('2026-09-25', '추석', 'API', TRUE),
('2026-09-26', '추석', 'API', TRUE),
('2026-10-03', '개천절', 'API', TRUE),
('2026-10-05', '대체공휴일', 'API', TRUE),
('2026-10-09', '한글날', 'API', TRUE),
('2026-12-25', '기독탄신일', 'API', TRUE);

-- 관리자 오버라이드 두 갈래를 dev에서 눈으로 확인할 수 있게 한 건씩 심는다.
INSERT INTO holiday (holiday_date, name, source, is_holiday) VALUES
('2026-08-17', '대체공휴일 아님(정정 예시)', 'MANUAL', FALSE),
('2026-12-24', '창립기념일(긴급 지정 예시)', 'MANUAL', TRUE);

INSERT INTO holiday_sync_marker (sync_year, synced_at) VALUES
(2025, CURRENT_TIMESTAMP),
(2026, CURRENT_TIMESTAMP);
