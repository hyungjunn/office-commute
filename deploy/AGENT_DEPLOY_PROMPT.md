# office-commute EC2 배포 실행 지시서 (에이전트 위임용)

당신은 office-commute 프로젝트를 EC2 단일 서버에 배포하는 작업을 수행한다.
이 문서가 유일한 지시서이며, 판단이 필요하면 `docs/DEPLOYMENT.md`(특히 §2.1, §5)를 근거로 삼는다.

## 입력값 (시작 전에 사람이 채움)

- `DOMAIN`: <배포 도메인>
- `SERVER`: <SSH 접속 문자열, 예: ubuntu@1.2.3.4 또는 ~/.ssh/config 의 호스트명>
- `IMAGE_TAG`: <배포할 커밋 SHA (git rev-parse --short HEAD)>
- 로컬 리포지토리 경로: 이 파일이 있는 리포지토리 루트
- DB: <RDS 엔드포인트 사용 / compose MySQL 추가> 중 하나

## 절대 규칙

1. **시크릿 원문을 읽거나 출력하지 않는다.** 서버의 `.env` 값 확인이 필요하면
   반드시 `grep -oE '^[A-Z_]+' .env`(키 이름만) 또는
   `docker compose -f docker-compose.prod.yml config --quiet`(성공 시 무출력, 실패 시 변수명만 출력)를 쓴다.
   `cat`/`less`/`echo`로 `.env`나 렌더된 compose 설정을 출력하는 것은 금지.
   예외: `APP_IMAGE=` 한 줄은 시크릿이 아니므로 에이전트가 추가/수정할 수 있다.
2. **파괴적 명령 금지**: DB drop/truncate, `docker volume rm`, `docker system prune`,
   `/etc/letsencrypt` 삭제·수정(certbot 명령 경유 제외), 서버의 기존 파일 덮어쓰기 전 백업 없이 진행 금지.
3. **사람 개입 지점(STOP)에 도달하면 진행을 멈추고 무엇을 해야 하는지 보고한 뒤 대기한다.**
4. 같은 명령이 실패하면 그대로 재시도하지 말고 원인을 조사한다. 한 단계에서 3회 실패하면 중단하고 상황을 보고한다.
5. 서버에서 git 조작이나 코드 수정을 하지 않는다. 서버에 올라가는 것은 배포 산출물(이미지, dist, deploy 파일)뿐이다.
6. 사람에게 비밀번호·키 등 시크릿 값을 채팅으로 요구하지 않는다. 필요한 시크릿은 전부 STOP 지점에서 사람이 서버에 직접 넣는다.

## 단계

### 0. 사전 점검 (로컬에서)

- `dig +short $DOMAIN` 이 서버 EIP를 가리키는지 확인. 아니면 STOP (DNS 전파 대기 또는 레코드 오류).
- `ssh $SERVER 'echo ok'` 접속 확인.
- 로컬 리포가 배포할 커밋(`IMAGE_TAG`)에 체크아웃되어 있고 clean 한지 확인.

### 1. 서버 셋업

- OS 확인 후 Docker Engine + compose 플러그인 설치 (이미 있으면 스킵).
- RAM 2GB 이하면 스왑 2GB 생성 (`/swapfile`, fstab 등록).
- 작업 디렉토리 생성: `~/office-commute/` (이하 서버 경로는 전부 이 아래).

### 2. 배포 파일 배치

- 로컬 `deploy/docker-compose.prod.yml`, `deploy/nginx.conf` 를 `~/office-commute/` 로 복사.
- 서버의 `nginx.conf` 에서 `office-commute.example.com` 을 `$DOMAIN` 으로 치환 (server_name 2곳 + 인증서 경로 2곳).

### 3. .env 준비 — ⛔ STOP (사람 개입)

- `~/office-commute/.env` 가 없으면 아래 템플릿을 생성한다 (**값은 비워둔 채**):
  ```
  APP_IMAGE='office-commute:<IMAGE_TAG>'
  DB_URL=''
  DB_USERNAME=''
  DB_PASSWORD=''
  PUBLIC_API_SERVICE_KEY=''
  ADMIN_PASSWORD_HASH=''
  MAIL_HOST=''
  MAIL_PORT=''
  MAIL_USERNAME=''
  MAIL_PASSWORD=''
  REPORT_MAIL_FROM=''
  REPORT_MAIL_CEO=''
  REPORT_MAIL_MANAGERS=''
  ```
- 사람에게 보고: "서버 `~/office-commute/.env` 에 값을 채워주세요. **모든 값은 작은따옴표로 감쌉니다**
  (특히 `ADMIN_PASSWORD_HASH='$2a$10$...'` — 따옴표가 없으면 해시가 잘립니다).
  메일 스모크(9단계) 전까지 `REPORT_MAIL_CEO` 는 운영자 본인 주소로 넣어주세요."
- 사람이 완료를 알리면 `docker compose -f docker-compose.prod.yml config --quiet` 로 필수 변수 충족을 검증한다
  (compose 가 `${VAR:?}` 로 강제하므로 누락 시 변수명이 에러로 나온다). 실패하면 부족한 변수명만 보고하고 다시 STOP.

### 4. TLS 최초 발급

- 서버에 `/etc/letsencrypt/live/$DOMAIN/fullchain.pem` 이 이미 있으면 스킵.
- 없으면: 80 포트가 비어 있는지 확인 후 `sudo certbot certonly --standalone -d $DOMAIN` (certbot 미설치 시 설치).
- 갱신 방식의 webroot 전환은 **6단계 이후**(dist 가 서버에 존재해야 검증이 통과한다)에,
  절대 규칙 2에 따라 renewal conf 를 직접 편집하지 말고 **certbot 명령으로만** 한다:
  ```
  sudo certbot reconfigure --cert-name $DOMAIN \
    --webroot -w /home/<SSH유저>/office-commute/dist \
    --deploy-hook 'docker compose -f /home/<SSH유저>/office-commute/docker-compose.prod.yml exec nginx nginx -s reload'
  ```
  `reconfigure` 는 내부 dry-run 이 성공해야만 갱신 설정을 바꾼다. 경로는 반드시 절대경로 —
  deploy-hook 과 갱신 타이머는 root 로 실행되므로 `~` 는 root 홈으로 풀린다.
  certbot 이 구버전이라 `reconfigure` 가 없으면 직접 편집하지 말고 STOP — 사람에게 보고.

### 5. 빌드 (로컬에서)

- 백엔드: `./gradlew bootJar` (**`build` 아님** — plain jar 가 생기면 Dockerfile COPY 가 깨진다)
  → `docker build -t office-commute:$IMAGE_TAG .`
- 프론트: `cd frontend && pnpm install --frozen-lockfile && pnpm build`
- 이미지 전송 (레지스트리 없이): `docker save office-commute:$IMAGE_TAG | ssh $SERVER 'docker load'`
- dist 전송: `rsync -az --delete frontend/dist/ $SERVER:~/office-commute/dist/`

### 6. 기동

- 서버에서: `cd ~/office-commute && docker compose -f docker-compose.prod.yml up -d`
- `docker compose ps` 로 app·nginx 모두 Up 확인. app 기동 완료까지 수십 초 동안 `/api` 가 502 인 것은 정상.
- `docker compose logs app` 에서 (값 출력 없이) 다음만 확인: Flyway 가 V1부터 순서대로 적용됐는지,
  "Started OfficeCommuteApplication" 이 찍혔는지, ERROR 레벨 로그가 없는지.
  Flyway 마이그레이션이 0건 적용(스킵)이면 즉시 STOP — 빈 DB 가 아니었다는 뜻이다 (DEPLOYMENT.md §5-3).

### 7. 비인증 검증 (전부 curl, 로컬에서 $DOMAIN 대상)

각 항목의 실제 응답 요약을 보고에 포함한다:

- `http://$DOMAIN` → 301 https 리다이렉트
- `https://$DOMAIN/` → 200, HTML (로그인 페이지)
- HTML 이 참조하는 `/assets/*.js` 하나 → 비로그인 상태로 200 (401 이면 실패)
- SPA 라우트 `https://$DOMAIN/teams`, `/employees`, `/overtime` → 각각 200 + index.html (XML/JSON 에러면 실패)
- `https://$DOMAIN/api/team` (비로그인) → 401 + JSON 에러 envelope
- `curl -i -X POST https://$DOMAIN/api/auth/login -H 'Content-Type: application/json' -d '{"email":"admin@company.com","password":"admin1234"}'`
  → **실패해야 정상** (V11 로테이션 확인). 성공하면 즉시 STOP — ADMIN_PASSWORD_HASH 주입이 안 된 것.
  이 응답의 `Set-Cookie: JSESSIONID=...` 에 `Secure; HttpOnly; SameSite=Lax` 가 모두 있는지 확인.
- `certbot renew --dry-run` (webroot 전환 후) → 성공 확인.

### 8. (compose MySQL 을 쓰는 경우에만) 백업 크론

- `mysqldump` 일일 크론 + 보관 7일 등록. RDS 면 이 단계 스킵 (자동 백업 확인만 보고에 명시).

### 9. 인증 필요 검증 — ⛔ STOP (사람 개입, 여기서 에이전트 작업 종료)

다음은 admin 자격증명이 필요하므로 사람이 직접 한다. 아래 목록을 보고에 포함하고 종료한다:

1. 새 비밀번호로 `https://$DOMAIN` 로그인 성공 확인 (실패하면 해시가 잘렸거나 오입력 — `.env` 따옴표 확인)
2. 팀/직원/초과근무 화면이 조회되는지 확인 (읽기 전용 — 데이터 생성 금지)
3. 메일 스모크: MANAGER 세션으로 `POST /api/overtime/report/dispatch?yearMonth=<지난달>` 호출
   → 응답 `status` 가 `SENT`(또는 미마감 시 `FAILED`+`UNCLOSED_COMMUTES`) 인지, **본인 메일함에 수신됐는지** 확인
4. 확인 후 `.env` 의 `REPORT_MAIL_CEO` 를 실제 대표 주소로 교체하고
   `docker compose -f docker-compose.prod.yml up -d app` 으로 app 재기동

## 보고 형식

작업 종료(또는 STOP) 시마다: ① 완료한 단계와 각 검증의 실제 결과 ② 실패·스킵한 것과 이유
③ 다음에 사람이 할 일. 추측으로 "됐을 것"이라고 쓰지 말고 실행한 명령의 실제 출력에 근거해서만 보고한다.
