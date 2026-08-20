# 갱신 배포 기록 (2026-08-20, as-built)

> 최초 구축 기록은 `DEPLOY_LOG_2026-08.md`, 전략은 `DEPLOYMENT.md`.
> 이 문서는 **가동 중인 서버에 변경을 얹은 첫 배포**의 기록이다. 최초 구축 때는
> 존재하지 않았던 문제(배포 순서, 기존 세션, 롤백 지점 확보)가 여기서 처음 나온다.

## 결과 요약

- **대상**: PR #51 — 내 근태에 출퇴근 시각 노출 + 미마감 여부 서버 판정
  (커밋 `948b3b7`, `cf79092` / 머지 `6d6a549`)
- **이미지**: `office-commute:7cec599` → **`office-commute:6d6a549`**
- **프론트 번들**: `index-CF28N2cb.js` (로컬 빌드와 해시 일치 확인)
- **소요**: 약 10분 (20:35~20:45 KST). 백엔드 다운타임 ~30초
- **DB 마이그레이션 없음** — 아래 §1 참조
- **롤백 지점**: 서버에 이전 이미지 `7cec599`와 이전 `dist.bak` 보존

## 1. 이번 배포의 두 가지 특성

### 1-1. 마이그레이션이 없다

이 변경은 응답에 `status`(`COMPLETED`/`IN_PROGRESS`/`UNCLOSED`/`DAY_OFF`)를 추가하지만
**컬럼을 만들지 않는다.** 상태는 시간에 따라 변하는 값이라(같은 행이 오늘 오후엔
`IN_PROGRESS`, 내일 아침엔 `UNCLOSED`) 저장하면 갱신 주체가 필요해지고 조용히 낡는다.
이미 있는 `work_end_time`·`work_zone`과 주입된 `Clock`으로 조회 시점에 계산한다.

→ 배포 시 Flyway 확인 절차가 통째로 불필요했다. prod 로깅이 `root=warn`이라
`flyway_schema_history`를 직접 조회해야 하는 번거로움(최초 구축 §4 참조)도 해당 없음.

### 1-2. 순서가 중요하다 — **백엔드 먼저, 프론트 나중**

이 변경은 프론트와 백엔드를 동시에 건드린다. 두 순서의 결과가 대칭이 아니다:

| 순서 | 중간 상태 | 결과 |
|---|---|---|
| 백엔드 먼저 | 새 백엔드 + 구 프론트 | 구 프론트가 늘어난 필드를 무시 — **화면 변화 없음(안전)** |
| 프론트 먼저 | 구 백엔드 + 새 프론트 | `workStartTime`·`status`가 안 와서 **표의 값이 전부 `—`** |

두 번째 증상을 배포 직전 로컬 리허설에서 실제로 밟았다(§3-1). 컬럼 헤더는 보이는데
값만 비는 모양이라 "기능이 안 만들어졌다"로 오해하기 쉽다.

## 2. 실행 순서 (실제로 돌린 것)

### 2-0. 사전 확인

```bash
git status --short                      # 클린
git rev-parse --short HEAD              # 6d6a549 (= origin/main)
ssh <서버> "cd ~/office-commute && grep ^APP_IMAGE= .env"   # 7cec599 ← 롤백 대상
ssh <서버> "docker images office-commute --format '{{.Tag}}'"  # 7cec599 보유 확인
```

**이전 이미지가 서버에 남아 있는지를 배포 전에 확인한다.** 없으면 롤백이 재빌드가 된다.

### 2-1. 백엔드 — jar만 보내고 서버에서 빌드

```bash
./gradlew bootJar                       # build 아님 (plain jar → COPY 두 파일 매칭 → 깨짐)
SHA=$(git rev-parse --short HEAD)
scp Dockerfile .dockerignore <서버>:~/office-commute/build-ctx/
scp build/libs/*.jar <서버>:~/office-commute/build-ctx/build/libs/
ssh <서버> "cd ~/office-commute/build-ctx && docker build -t office-commute:$SHA ."
```

로컬 Mac은 ARM, EC2는 x86이라 이미지를 로컬에서 만들면 서버에서 돌지 않는다.
jar는 플랫폼 무관이므로 jar만 옮긴다 (최초 구축 §1-3에서 확립된 방식).

### 2-2. 전환

```bash
cd ~/office-commute
cp .env .env.bak.$(date +%Y%m%d-%H%M%S)                     # 편집 전 백업
sed -i "s|^APP_IMAGE=.*|APP_IMAGE='office-commute:$SHA'|" .env
docker compose -f docker-compose.prod.yml -f docker-compose.mysql.yml up -d app
```

`.env`는 작은따옴표 규칙을 지킨다(BCrypt 해시의 `$` 보간 문제 — 최초 구축 §0).
`-f` 두 개는 항상 함께 (MySQL 오버레이).

컨테이너는 `Recreate → mysql healthy 대기 → Started` 순으로 올라왔다.

### 2-3. 백엔드 확인

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://office-commute.com/api/auth/me   # 401
docker compose -f ... -f ... ps    # app  office-commute:6d6a549  Up
```

`401`이 정상이다 — 미로그인 상태의 에러 봉투가 돌아온다는 것은 컨트롤러·인터셉터가
정상 기동했다는 뜻이다.

### 2-4. 프론트 — 백업 후 교체, 무중단

```bash
cd frontend && pnpm build
ssh <서버> "cd ~/office-commute && rm -rf dist.bak && cp -r dist dist.bak"   # 롤백 지점
rsync -az --delete frontend/dist/ <서버>:~/office-commute/dist/
ssh <서버> "cd ~/office-commute && docker compose -f ... -f ... exec -T nginx nginx -s reload"
```

nginx가 `dist`를 직접 서빙하므로 백엔드 재시작 없이 파일 교체 + reload로 끝난다.
`exec`에 **`-T`**를 붙인다 (TTY 없는 환경 — 최초 구축 트러블슈팅 #7과 같은 이유).

### 2-5. 배포 검증

```bash
curl -s https://office-commute.com/ | grep -o 'index-[A-Za-z0-9]*\.js'
ls frontend/dist/assets/*.js
```

두 해시가 같으면 서버가 방금 빌드한 번들을 서빙하고 있는 것이다. 캐시나 rsync 누락을
한 번에 걸러낸다.

## 3. 트러블슈팅 기록

### 3-1. (로컬 리허설) 컬럼 헤더는 나오는데 값이 전부 `—`

**증상.** 로컬 full 프로파일에서 내 근태를 열었더니 출근·퇴근·근무 시간 헤더는 있는데
모든 행의 값이 `—`, 총 근무 `0시간`.

**원인.** `build/libs`의 jar가 **8월 14일 빌드**였다. `docker-compose.yml`의 `app`은
`build: .` → `Dockerfile`이 `COPY build/libs/*.jar` 하므로 이미지가 구 코드로 만들어졌다.
반면 nginx는 `./frontend/dist`를 볼륨으로 물어 프론트만 최신이었다.

**해결.** `./gradlew bootJar && docker compose --profile full up -d --build app`.
`--build`를 빼면 기존 `office-commute:local` 이미지를 재사용해 증상이 그대로다.

**남긴 것.** 이 증상이 §1-2의 "프론트 먼저 배포"와 정확히 같은 모양이다. 운영 배포 순서를
백엔드 선행으로 고정한 근거가 여기서 나왔다.

### 3-2. 상태 4종을 하루에 한 번밖에 볼 수 없는 문제

**증상.** `IN_PROGRESS`/`UNCLOSED`를 화면에서 확인하려는데, 같은 날 출근은 한 번만
가능하고(`uk_commute_history_employee_date`) 이전 근무가 열려 있으면 출근이 막힌다
(`PreviousCommuteNotEndedException`). 미퇴근 상태를 만들려면 하루를 넘겨야 한다.

**해결.** 도메인 제약은 API 경로에만 걸리므로 **로컬 DB에 직접 INSERT**해서 네 상태를
한 화면에 만들었다. 주의: 이 스택의 저장값은 **UTC**다(연차 행이 `work_date=08-19`인데
`work_start_time=08-18 15:00`인 것이 증거). KST에서 9시간을 빼서 넣는다.

**원칙.** 운영 DB에는 하지 않는다. 운영 확인은 "오늘 출근 찍고 `근무 중`이 뜨는가" 하나면
충분하고, `UNCLOSED`는 실제 미마감이 생기면 저절로 드러난다 — 그게 이 기능의 목적이다.
네 상태의 판정 로직 자체는 `CommuteHistoryTest.status_*`가 임의 시각을 주입해 이미 덮는다.

### 3-3. 운영 API 계약 검증 미완 (열린 항목)

`curl`로 `GET /api/commute`까지 확인하려 했으나 운영 admin 비밀번호가 로컬(`admin1234`)과
달라 로그인이 401로 막혔다. 운영 비번은 DB 직접 UPDATE로 설정된 값이라(최초 구축 §5 잔여
과제) 문서에도 `.env`에도 정본이 없다. **브라우저 수동 확인으로 대체.**

→ `.env`의 `ADMIN_PASSWORD_HASH` 정상화와 비밀번호 변경 API 부재가 여기서 다시 비용이 됐다.

## 4. 롤백 절차 (준비만, 미실행)

```bash
cd ~/office-commute
sed -i "s|^APP_IMAGE=.*|APP_IMAGE='office-commute:7cec599'|" .env
docker compose -f docker-compose.prod.yml -f docker-compose.mysql.yml up -d app
rm -rf dist && mv dist.bak dist
docker compose -f docker-compose.prod.yml -f docker-compose.mysql.yml exec -T nginx nginx -s reload
```

이번 변경은 스키마를 건드리지 않으므로 **데이터 롤백이 필요 없다.** 이미지와 정적 파일만
되돌리면 완전히 이전 상태가 된다.

## 5. 다음 갱신 배포 체크리스트

- [ ] `git status` 클린 + `HEAD == origin/main`
- [ ] 현재 `APP_IMAGE` 기록 (= 롤백 대상), 해당 이미지가 서버에 있는지 확인
- [ ] 스키마 변경 있나? 있으면 롤백이 데이터를 포함하므로 별도 계획 필요
- [ ] `./gradlew bootJar` (`build` 아님) → jar scp → 서버 `docker build`
- [ ] `.env` 백업 → `APP_IMAGE` 교체 → `up -d app` → `/api/auth/me` 401 확인
- [ ] 서버 `dist` → `dist.bak` 백업 → `pnpm build` → rsync → `nginx -s reload` (`-T`)
- [ ] 번들 해시 로컬/서버 일치 확인 → 브라우저 확인
- [ ] **app 재시작은 전 직원 로그아웃**이다 — 근무 시간대를 피한다

## 6. 남은 과제

- [ ] 운영 `.env`의 `ADMIN_PASSWORD_HASH` 정상화 (§3-3 — 운영 검증을 사람 손에 의존하게 만든다)
- [ ] 세션이 메모리에 있어 배포마다 전원 로그아웃 (`DEPLOYMENT.md` §6의 Spring Session + Redis)
- [ ] 배포 스크립트화 — 이번 배포는 명령을 손으로 이어붙였다. 순서(백엔드→프론트)와
      백업 지점(`.env`, `dist`) 확보가 사람 기억에 의존한다
