# 배포 전략 (Deployment Strategy)

> 작성일: 2026-07-09 · 대상: office-commute (Spring Boot 3.5 / Java 21 백엔드 + React 18 SPA 관리자 프론트)
> 전제: 사내 소규모 관리자용 서비스, 세션 쿠키(JSESSIONID) 기반 인증, 단일 리전.

## 0. 결론 요약

- 인증 방식이 **서버 세션 + 쿠키**이고 백엔드에 CORS 설정이 없으므로, 프론트와 백엔드는 반드시 **같은 오리진**으로 배포한다. (이 전제는 코드 전반 — vite 프록시, `client.ts`의 상대 baseUrl — 에 이미 깔려 있다.)
- 권장 아키텍처: **단일 서버 + Docker Compose** — Nginx(TLS 종료 + SPA 정적 서빙 + `/api` 리버스 프록시) / Spring Boot 컨테이너 / MySQL(가능하면 관리형 DB).
- 단, **현재 코드 그대로는 어떤 방식으로도 배포가 동작하지 않는다.** 아래 Phase 0의 배포 차단 이슈 4건을 먼저 해결해야 한다 (2026-07-09 검증 결과, 상당수 실기동으로 실증됨).

## 1. Phase 0 — 배포 차단 이슈 (코드 수정 선행)

### 1-1. API 경로를 `/api` 프리픽스로 통일 (차단)
현재 API가 `/team`, `/employee`, `/commute`, `/annual-leave`, `/overtime` 등 루트에 흩어져 있고 auth만 `/api/auth`다. 이 때문에:

- SPA 라우트 `/overtime`이 백엔드 `GET /overtime` API와 **정확히 충돌** — 새로고침/딥링크 시 페이지 대신 XML 에러가 반환된다 (실증됨).
- vite 프록시가 프리픽스 매칭이라 dev에서도 `/teams`, `/employees` 새로고침 시 백엔드 500이 나온다 (실증됨).
- 리버스 프록시 라우팅 규칙이 "백엔드 경로 목록"을 계속 따라가야 하는 유지보수 함정이 된다.

**수정**: 전 컨트롤러 경로를 `/api/**`로 이동 + `openapi.yml` 갱신 + `pnpm gen:api` 재생성 + vite 프록시를 `/api` 하나로 축소. 이후 프록시 규칙은 영원히 "`/api/**` → 백엔드, 나머지 → SPA" 한 줄이다.

> 빠른 우회(비권장): 백엔드 경로를 못 바꾸면 SPA 라우트 `/overtime`을 `/overtimes`로 개명하고 Nginx에 백엔드 경로 목록을 하드코딩. 충돌 함정이 남으므로 임시책으로만.

### 1-2. `AuthInterceptor`가 정적 리소스를 차단 (차단)
인터셉터가 `/**`에 적용되고 제외가 `/`, `/error`, `/api/auth/**`, `/h2-console/**`뿐이라, 비로그인 상태에서 `/assets/*.js` 요청이 401이 된다 — **로그인 페이지 자체가 로드되지 않는다** (실증됨). 1-1 완료 후 인터셉터를 `/api/**`에만 적용하도록 뒤집으면 해결된다. 죽은 예외인 `/h2-console/**`은 이때 제거한다.

### 1-3. SPA 서빙 경로 부재 (차단)
`frontend/dist`를 서빙하는 주체가 없다 (jar 포함 태스크 없음, `static/` 없음, SPA fallback 없음). 본 문서는 **Nginx가 dist를 서빙**하는 방식을 채택한다(§2). Spring static 서빙(단일 jar)을 원하면 dist 복사 Gradle 태스크 + index.html fallback 컨트롤러가 추가로 필요하므로, 운영 단순화를 위해 Nginx 방식을 권장한다.

### 1-4. 시드 admin 계정의 공개된 비밀번호 (차단)
`V5__seed_admin.sql`이 `admin@company.com` / `admin1234`(주석에 평문 명시, `scripts/api_test.sh`에도 노출)를 prod에 그대로 시드한다. 비밀번호 변경 API도 없다.

**수정**: 새 마이그레이션 `V7__rotate_admin_password.sql`에서 Flyway placeholder로 해시를 주입한다.

```sql
-- V7__rotate_admin_password.sql
UPDATE employee SET password = '${admin_password_hash}' WHERE email = 'admin@company.com';
```

```yaml
# application-prod.yml
spring:
  flyway:
    placeholders:
      admin_password_hash: ${ADMIN_PASSWORD_HASH}
```

배포 전 운영자가 BCrypt 해시를 생성해 `ADMIN_PASSWORD_HASH`로 주입한다. (적용 후 값 변경은 재실행되지 않으므로, 중기적으로 비밀번호 변경 API 추가 필요 — 기존 리뷰 잔여 항목 E와 동일.)

## 2. 목표 아키텍처

```
[브라우저]
   │ HTTPS (443)
   ▼
[Nginx]  ── TLS 종료 (Let's Encrypt/certbot)
   ├── /api/**  → proxy_pass http://app:8080  (쿠키/헤더 전달)
   └── 그 외    → frontend dist 정적 서빙, 미매칭 경로는 index.html (SPA fallback)
        │
   [app: Spring Boot jar, prod 프로파일]  ── 단일 인스턴스
        │
   [MySQL 8]  ── 관리형 DB(RDS 등) 권장, 불가 시 Compose 컨테이너 + 볼륨
```

Nginx 핵심 설정 (1-1 완료 기준):

```nginx
server {
    listen 443 ssl;
    # ... ssl_certificate ...

    root /srv/office-commute/dist;

    location /api/ {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri /index.html;   # SPA fallback
    }
}
server { listen 80; return 301 https://$host$request_uri; }
```

백엔드 prod 프로파일 추가 설정 (HTTPS 프록시 뒤 세션 쿠키 보안 — 현재 미설정, 검토에서 확인됨):

```yaml
# application-prod.yml 에 추가
server:
  forward-headers-strategy: framework   # X-Forwarded-* 신뢰
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
        same-site: lax
```

**선택 근거**
- *단일 서버 + Compose인 이유*: 관리자용 소규모 트래픽, 세션이 톰캣 인메모리라 어차피 수평 확장 불가(스케일아웃하려면 Spring Session + Redis 선행 필요 — §6). 지금 쿠버네티스/멀티 인스턴스는 과잉.
- *Nginx가 dist를 서빙하는 이유*: Spring static 방식은 1-3의 추가 구현이 필요하고, 프론트만 고치는 배포에도 백엔드 재시작(=전 직원 세션 로그아웃)이 강제된다. 분리하면 프론트 배포는 파일 교체 + reload로 무중단.
- *관리형 DB 권장 이유*: 백업/패치 자동화. 불가 시 Compose MySQL + `mysqldump` 일일 크론 백업을 최소선으로.

## 3. 빌드·배포 파이프라인 (GitHub Actions 권장)

현재 CI가 없다(`.github/`에 이슈 템플릿뿐). 최소 파이프라인:

1. **CI (PR/main push)**: `./gradlew check` (테스트 + openApiValidate) + `cd frontend && pnpm install --frozen-lockfile && pnpm build` (tsc 포함). Testcontainers 사용하므로 러너에 Docker 필요 (Testcontainers 1.21.4 핀 유지).
2. **CD (main 태그/수동 트리거)**:
   - 백엔드: `./gradlew bootJar` → Dockerfile(eclipse-temurin:21-jre)로 이미지 빌드 → 레지스트리 push → 서버에서 `docker compose pull && up -d app`.
   - 프론트: `pnpm build` → `dist/`를 서버 정적 경로로 rsync → `nginx -s reload`.
3. **롤백**: 이미지 태그를 커밋 SHA로 지정해 이전 태그로 `up -d`. 프론트는 이전 dist 디렉토리 심볼릭 링크 전환.

> ⚠️ Flyway 마이그레이션이 포함된 릴리스는 앱 롤백만으로 스키마가 돌아가지 않는다. 마이그레이션은 하위 호환(additive)으로 작성하고, 파괴적 변경(컬럼 drop 등)은 앱 배포와 분리해 한 릴리스 뒤에 적용한다 (V3, V6 스타일의 drop은 특히 주의).

## 4. 환경변수 / 시크릿

`.env.example` 기준 + 이번에 추가되는 항목:

| 변수 | 값 (prod) | 비고 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 운영 DB | 기본값 없음(`DB_PASSWORD`) — 미설정 시 부팅 실패가 정상 |
| `PUBLIC_API_SERVICE_KEY` | 공공데이터포털 키 | 미설정 시 부팅 실패 — 배포 전 발급 확인 |
| `ADMIN_PASSWORD_HASH` | BCrypt 해시 | **신규** (§1-4) |
| `SERVER_PORT` | 8080 (컨테이너 내부) | |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 사내 SMTP 또는 Gmail 앱 비밀번호 | **신규** (ADR 3). prod 기본값 없음 — 미설정 시 부팅 실패 |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` | **신규**. 선택 — 미설정 시 둘 다 true |
| `REPORT_MAIL_FROM` | 발신 주소 | **신규**. prod 기본값 없음 |
| `REPORT_MAIL_CEO` | 대표 메일 | **신규**. 정상 리포트의 유일한 수신자 |
| `REPORT_MAIL_MANAGERS` | 근태 관리자 메일 | **신규**. 쉼표 구분 복수 가능. 미마감 보류·발송 실패 알림 수신자 |
| `PUBLIC_API_CONNECT_TIMEOUT` / `PUBLIC_API_READ_TIMEOUT` | `3s` / `5s` | **신규**. 선택 — 배치가 공휴일 API 에 매달려 재시도 창을 잡아먹지 않도록 재배포 없이 조정 |

> 메일 설정에 기본값을 두지 않는 이유: 배치는 매월 1일에만 돈다. 값이 비어 있어도 앱이
> 뜨면 한 달 뒤 "리포트가 안 왔다"로 발견되고, 그 시점엔 이미 급여 정산이 지나 있다.
> `ADMIN_PASSWORD_HASH`와 같은 방침으로 부팅 시점에 실패시킨다.

시크릿은 서버의 `.env`(gitignored) 또는 GitHub Actions Secrets로만 관리한다. docker-compose.yml의 로컬용 고정 비밀번호(`office_password` 등)를 운영에 재사용하지 않는다.

## 5. 배포 당일 체크리스트

1. Phase 0 4건 머지 확인 (§1) + `schema.d.ts` 재생성 커밋 (스펙 드리프트 검토 결과: 현재 stale).
2. `./gradlew check` 통과 + `pnpm build` 통과.
3. 운영 DB 생성(빈 스키마) → 앱 첫 기동 시 Flyway가 V1부터 적용되는지 로그 확인. (`baseline-on-migrate: true`는 빈 DB에선 무해하나, 기존 수동 스키마가 있는 DB에 물리면 마이그레이션이 스킵되므로 반드시 빈 DB에서 시작.)
4. `ADMIN_PASSWORD_HASH` 주입 확인 → 배포 후 `admin1234`로 로그인이 **실패**하는 것 확인.
5. HTTPS로 로그인 → `Set-Cookie`에 `Secure; HttpOnly; SameSite=Lax` 확인.
6. 비로그인 상태에서 루트 접속 → 로그인 페이지 정상 로드(정적 자산 401 없음) 확인.
7. SPA 라우트(`/teams`, `/employees`, `/overtime`) 직접 접속/새로고침 정상 확인.
8. `scripts/api_test.sh`를 운영 오리진으로 스모크 실행 (admin 자격증명은 새 값으로).
9. DB 백업 크론 + 로그 로테이션(`logs/office-commute.log`, 설정상 100MB/30일) 동작 확인.
10. 메일 설정 주입 확인 → `POST /api/overtime/report/dispatch?yearMonth=<지난달>`을 MANAGER 로 한 번 호출해
    응답의 `status`가 `SENT`(또는 미마감이 있으면 `FAILED` + `UNCLOSED_COMMUTES`)로 나오는지 확인.
    배치 첫 발화(다음 달 1일 06:00 KST)를 기다리지 않고 발송 경로 전체를 검증하는 방법이다.
    이미 발송된 달이면 아무 일도 일어나지 않으므로(멱등) 스모크로 안전하다.

## 6. 배포 후 개선 로드맵 (권고 — 차단 아님)

| 우선순위 | 항목 | 근거 |
|---|---|---|
| 높음 | 로그인 실패 메시지 통일 + 더미 해시 매칭 | 현재 "존재하지 않는 이메일" vs "비밀번호 불일치" 구분 응답 → 계정 열거 가능 |
| 높음 | 로그인 rate limit (Nginx `limit_req`로 즉시 가능) | 브루트포스 방어 부재, 고정 admin 계정과 결합 시 위험 |
| 높음 | 프론트 401 전역 처리 → 로그인 리다이렉트 | 세션 만료(기본 30분) 시 토스트만 반복되는 UX (검토 실증) |
| 중간 | 비밀번호 변경 API | 현재 로테이션 수단이 DB 직접 수정뿐 |
| 중간 | `spring-boot-starter-actuator` 추가, `/actuator/health`만 노출 | 헬스체크/모니터링 기반 (현재 미포함) |
| 중간 | XML 컨버터 제거 또는 JSON 강제 | `jackson-dataformat-xml`로 콘텐츠 협상 시 XML 에러 노출 (검토 실증) |
| 중간 | `GET /commute` 전용 응답 DTO + 스펙 정합 (`workingMinutes`), `non_null` inclusion 재검토 | 스펙 드리프트 2건 (검토 실증) |
| 낮음 | Spring Session + Redis | 재배포 시 전원 로그아웃 해소 + 수평 확장 전제. 트래픽상 당장 불필요 |
| 낮음 | Spring Boot 4.x 업그레이드 계획 | 3.5는 2026년 중반 OSS 지원 종료 라인 — 정확한 일정은 spring.io 지원 표에서 확인 후 분기 내 계획 수립 권장 |

## 7. 이번 검증에서 "이상 없음"으로 확인된 범위

- **API 계약**: 전 컨트롤러 경로/메서드/상태코드/필드/에러 envelope가 `openapi.yml`과 일치 (dev 프로파일 실기동 + curl 실증). 날짜 직렬화(`yyyy-MM-dd`, `yyyy-MM`), enum(Role), 빈 200 응답 처리 정상.
- **인가 구조**: `AuthInterceptor` + `@ManagerOnly` + 세션 `currentEmployeeId` 조합 견고. IDOR 없음(본인 리소스는 세션 ID만 사용), 경로 매칭 우회 없음, 세션 고정 방어 구현·테스트 존재, 응답에 비밀번호 해시 미노출.
- **프론트 인증 처리**: 토큰을 localStorage에 두지 않음(세션 쿠키 + `credentials: 'include'`) — XSS로 탈취될 자격증명 없음.
