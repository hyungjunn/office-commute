# 첫 운영 배포 기록 (2026-08-14 ~ 08-16, as-built)

> DEPLOYMENT.md 가 "어떻게 배포해야 하는가"(전략)라면, 이 문서는 "실제로 어떻게 했고
> 무엇이 터졌고 어떻게 잡았는가"(기록)다. 다음 배포·장애 대응 때 이 문서부터 본다.

## 결과 요약

- **URL**: https://office-commute.com (주) + https://13-125-8-177.sslip.io (레거시 — 이력서 제출 URL, 함께 서빙)
- **서버**: EC2 t3.small (서울), Ubuntu 24.04 LTS, EIP `13.125.8.177`, 스왑 2GB
- **스택**: nginx(TLS 종료) + Spring Boot(prod 프로파일, 이미지 태그 = 커밋 SHA) + MySQL 8 컨테이너
  - 서버 `~/office-commute/` 에서 `docker compose -f docker-compose.prod.yml -f docker-compose.mysql.yml`
- **DB**: RDS 대신 compose MySQL(`deploy/docker-compose.mysql.yml` 오버레이) + 일일 백업 크론
- **TLS**: Let's Encrypt 도메인별 2장, 갱신 webroot + deploy-hook(nginx reload)

## 0. 배포 전 준비 (08-14 ~ 08-16 오전)

1. **DEPLOYMENT.md 전면 검증** — 문서(07-09 작성)의 주장을 코드와 전수 대조.
   Phase 0 차단 4건은 이미 해소된 상태였고(문서만 낡음), 체크리스트 2건의 사고 소지를 발견해 수정:
   - `api_test.sh` 운영 실행 금지 (admin 비번 하드코딩 + 생성 데이터 cleanup 없음)
   - 메일 스모크가 신규 DB 에서 실제 CEO 에게 빈 리포트를 발송하는 문제 → `REPORT_MAIL_CEO` 임시 교체 절차 명시
2. **compose fail-fast 강제** (외부 리뷰 지적, 실측 재현 후 수용) — compose 는 미설정 변수를
   빈 문자열로 "존재하게" 주입해 Spring placeholder fail-fast 를 무력화한다. 필수 변수 13종에
   `${VAR:?}` 적용 + `.env` 작은따옴표 규칙 문서화 (BCrypt 해시의 `$` 가 보간으로 잘리는 문제 실측).
3. **위임 지시서 작성** — `deploy/AGENT_DEPLOY_PROMPT.md` (STOP 지점 2곳, 시크릿 열람 금지 규칙).

## 1. 배포 당일 실행 순서 (08-16)

### 1-1. AWS 리소스 (수동, 콘솔)

- EC2 t3.small + Ubuntu 24.04, 키페어 `office-commute.pem` (`~/.ssh/`, chmod 400)
- 보안 그룹: 22(내 IP), 80/443(전체). **8080·3306 은 비공개**
- EIP 할당·연결 → 도메인 없이 `13-125-8-177.sslip.io` 사용 (sslip.io 는 IP를 그대로 도메인으로 풀어줌 — DNS 등록·전파 대기 불필요)

### 1-2. 서버 셋업

```bash
# 스왑 2GB (t3.small 2GB RAM 보완) + fstab 등록
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
```

### 1-3. 이미지 빌드 — **서버에서** (중요)

로컬 Mac 은 ARM, EC2 는 x86 이라 로컬 빌드 이미지는 서버에서 돌지 않는다.
jar 는 플랫폼 무관이므로 **jar 만 전송해 서버에서 빌드**한다:

```bash
./gradlew bootJar                       # build 아님 — plain jar 가 생기면 Dockerfile COPY 가 깨짐
scp Dockerfile .dockerignore <서버>:~/office-commute/build-ctx/
scp build/libs/*.jar <서버>:~/office-commute/build-ctx/build/libs/
ssh <서버> 'cd ~/office-commute/build-ctx && docker build -t office-commute:<커밋SHA> .'
```

### 1-4. 배포 파일 배치

- `deploy/docker-compose.prod.yml`, `deploy/docker-compose.mysql.yml`, `deploy/nginx.conf` → 서버 `~/office-commute/`
- 프론트: `pnpm build` → `rsync -az --delete frontend/dist/ <서버>:~/office-commute/dist/`

### 1-5. TLS 최초 발급 (compose 기동 **전**)

nginx.conf 가 인증서 존재를 전제하므로 순서가 중요하다. 80 포트가 빈 상태에서:

```bash
sudo certbot certonly --standalone -d 13-125-8-177.sslip.io
```

### 1-6. 시크릿 주입 (.env)

- 키 이름만 채운 템플릿을 만들어 두고, 값은 운영자가 서버에서 직접 기입 (**전부 작은따옴표**)
- 검증은 값을 보지 않고: `docker compose -f ... -f ... config --quiet` — `${VAR:?}` 덕에 누락·빈 값이면 변수명만 에러로 나온다

### 1-7. 기동 + 검증

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.mysql.yml up -d
```

- **Flyway 확인은 로그가 아니라 DB 로**: prod 로깅이 `root: warn` 이라 Flyway INFO 가 안 찍힌다.
  `exec -T mysql sh -c 'mysql -uoffice_user -p"$MYSQL_PASSWORD" office_commute -e "SELECT version, success FROM flyway_schema_history"'`
  → V1~V14 전부 success=1 확인
- 비인증 검증 (curl): 80→443 리다이렉트 / 루트 200 / 정적 자산 비로그인 200 / SPA 라우트(`/teams` 등) 200 /
  `/api/team` 401 envelope / **`admin1234` 로그인 실패**(=V11 로테이션 적용 증명) 전부 통과
- 인증 검증 (운영자): 새 비밀번호 로그인, 메일 스모크
  `POST /api/overtime/report/dispatch?yearMonth=<지난달>` → `status: SENT` + 실수신 확인

### 1-8. 갱신·백업 마무리

- certbot 갱신을 webroot 로 전환: `sudo certbot reconfigure --cert-name <도메인> --webroot -w /home/ubuntu/office-commute/dist --deploy-hook "docker compose -f ... -f ... exec -T nginx nginx -s reload"`
  (renewal conf 직접 편집 금지 — reconfigure 는 내부 dry-run 통과 시에만 반영)
- DB 백업: `~/office-commute/backup.sh` (mysqldump | gzip, 7일 보관) + 크론 `30 4 * * *`

## 2. 도메인 연결 (08-16 저녁)

1. Cloudflare 에서 `office-commute.com` 구매 → A 레코드 `@` → EIP, **Proxy 는 DNS only(회색)**, TTL Auto
2. 무중단 발급 (nginx 뜬 채로): `sudo certbot certonly --webroot -w ~/office-commute/dist -d office-commute.com`
3. nginx 를 도메인별 ssl server 블록 2개로 재구성 (기존 sslip.io URL 유지) → `nginx -t` → reload
4. `deploy/nginx.conf` 템플릿을 서버 실물과 동일하게 커밋 (드리프트 제거)

## 3. 트러블슈팅 기록 (발생 순)

| # | 증상 | 원인 | 해결 |
|---|---|---|---|
| 1 | 로컬 docker build 실패 | Docker Desktop 미기동 + (근본적으로) ARM/x86 불일치 | 서버 빌드로 전환 (§1-3) |
| 2 | admin 로그인 불가 (새 비번도) | 해시 생성 명령에 `-b` 누락 → 인자가 사용자명으로 해석되고 `tr` 이 콜론까지 지워 "사용자명+해시" 붙은 깨진 값이 DB에 들어감 | `htpasswd -nbBC 10 "" '<비번>' \| cut -d: -f2` 로 재생성 → DB 직접 UPDATE |
| 3 | 복구 블록에서 `read: -p: no coprocess` | macOS zsh 는 bash 의 `read -p` 를 다르게 해석 → 비밀번호가 빈 값으로 잡힘 | `printf "..."; read -r -s PW` 로 교체 |
| 4 | curl 로그인만 실패 (브라우저는 성공) | 비밀번호를 셸 문자열로 JSON 에 직접 삽입 → 특수문자/오타에 취약 | `jq -n --arg` 로 JSON 생성 |
| 5 | 메일 발송 `Authentication failed` 가 `.env` 수정 후에도 지속 | **`.env` 수정은 떠 있는 컨테이너에 반영되지 않음** — 재생성(`up -d`) 필요 | app 재기동. 추가로 Gmail 은 앱 비밀번호(공백 제거) 필수 |
| 6 | app 크래시 루프: `Access denied for user 'office_user'` | `.env` 편집 중 `DB_PASSWORD` 가 변형 — MySQL 은 **최초 기동 때의 비밀번호를 볼륨에 고정**하므로 `.env` 와 어긋남 | 볼륨 쪽을 `.env` 에 맞춰 동기화: 컨테이너 env 의 root 비번으로 `ALTER USER 'office_user'@'%' IDENTIFIED BY '<새값>'` (값 미출력 파이프 방식) |
| 7 | certbot deploy-hook 이 에러 출력 | TTY 없는 갱신 환경에서 `docker compose exec` 에 `-T` 누락 | 두 인증서 모두 `reconfigure` 로 훅에 `-T` 추가 |

## 4. 운영 치트시트

```bash
# 접속
ssh -i ~/.ssh/office-commute.pem ubuntu@13.125.8.177
# compose (항상 -f 2개)
cd ~/office-commute
docker compose -f docker-compose.prod.yml -f docker-compose.mysql.yml <ps|logs app|up -d>
# 백엔드 재배포 (로컬에서): bootJar → jar scp → 서버 build (§1-3) → up -d app
# 프론트만 재배포: pnpm build → dist rsync → exec -T nginx nginx -s reload  (백엔드 무중단)
# 갱신 리허설: sudo certbot renew --dry-run
```

**꼭 기억할 것**
- `.env` 수정 후에는 반드시 `up -d` (컨테이너 재생성) — reload 개념이 없다
- `DB_PASSWORD` 를 바꾸려면 `.env` 와 MySQL(`ALTER USER`) 을 **함께** 바꿔야 한다
- MySQL 데이터는 `mysql-data` 볼륨에 있다 — 컨테이너 재생성은 안전, **볼륨 삭제만 데이터 소실**

## 5. 잔여 과제

- [ ] 서버 `.env` 의 `ADMIN_PASSWORD_HASH` 정상 해시로 교체 (지금은 DB 직접 수정으로 우회 중 — DB 볼륨을 새로 만들 때만 문제가 됨)
- [x] `DB_PASSWORD` 값 정리 — 08-16 완료. 서버에서 `openssl rand -base64 15` 로 생성해
      MySQL `ALTER USER` 와 `.env` sed 교체를 한 스크립트에서 원자적으로 수행 후 `up -d`.
      (트러블슈팅 #6 의 편집 사고 값 제거. 검증: 앱 재기동 + DB 연결 + 백업 스크립트 정상)
- [ ] 인증서 갱신을 DNS-01(`certbot-dns-cloudflare` + API 토큰) 또는 Cloudflare Origin CA 로 전환 —
      현재 webroot(HTTP-01) 방식은 80 포트 개방에 의존한다. Cloudflare 프록시(주황 구름)를 켜거나
      80 포트를 닫고 싶어지는 시점에 전환. 프록시 상시 on 이면 Origin CA(15년 유효, 갱신 불필요)가
      관리 포인트 최소, 프록시를 안 켤 거면 DNS-01 이 정석. DNS-01 은 API 토큰(Zone DNS Edit)을
      서버에 자격증명 파일로 보관해야 함(600)
- [ ] CD 첫 운영 실행 검증 — ECR/S3/SSM 기반 workflow와 서버 스크립트는 구현됨. 첫 성공 전까지는 위 치트시트의 수동 절차를 복구 경로로 유지
- [ ] DEPLOYMENT.md §6 로드맵 "높음" 3건 (로그인 실패 메시지 통일, nginx `limit_req`, 프론트 401 리다이렉트)
