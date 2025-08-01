INSERT INTO employee (name, role, birthday, work_start_date, employee_code, password)
VALUES ('관리자', 'MANAGER', '1990-01-01', '2024-01-01', 'ADMIN001',
        '{bcrypt}$2a$10$A3k/Wat/KaWqfECeP9aZ8Of40WisJDB6le8KVYWAVoGRbgMLMXTcS');
-- 비밀번호: admin123!

INSERT INTO team (name, manager_name, member_count, annual_leave_criteria)
VALUES ('관리팀', '관리자', 1, 15);
