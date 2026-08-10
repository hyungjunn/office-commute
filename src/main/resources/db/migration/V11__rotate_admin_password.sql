-- V5 가 시드한 admin 계정의 비밀번호는 평문(admin1234)이 저장소에 노출되어 있다.
-- 운영 배포 전 반드시 로테이션한다 (docs/DEPLOYMENT.md §1-4).
-- 해시는 Flyway placeholder 로 주입한다:
--   prod  → ADMIN_PASSWORD_HASH 환경변수 (미설정 시 부팅 실패가 정상)
--   mysql/test → 기존 dev 해시가 기본값 (로컬 로그인 흐름 유지)
-- 적용 후 placeholder 값만 바꿔도 재실행되지 않는다 — 이후 로테이션은 비밀번호 변경 API(중기 과제)로.
UPDATE employee
SET password = '${admin_password_hash}'
WHERE email = 'admin@company.com';
