# TODOs

## 해결 과정

1. 공휴일을 날짜 단위 행으로 DB에 저장한다. DB가 source of truth이며, 계산 경로는 외부 API를 직접 호출하지 않는다.
2. 매일 새벽 동기화 (올해 ~ +2년, 연 단위, 멱등):
   - 연 단위 조회(solYear만 지정, numOfRows=100)로 한 페이지에 전부 수신한다.
   - 적재 전 검증: 기대 스키마로 파싱 성공(포털 게이트웨이 오류는 스키마가 달라 여기서 걸러짐) → resultCode=00 → 수신 건수 == totalCount → 항목별 검증(locdate 형식, 요청 **연도** 일치, dateName 존재). HTTP 200이어도 실패일 수 있음.
   - **연간 0건 응답은 실패로 간주한다.** 월 단위 0건은 정상(4월·11월)이지만, 한국에 공휴일 0개인 해는 없다 — 연간 0건은 "+2년차 미발표"이므로 적재하지 않고 마커도 세우지 않는다. 이것이 "존재하는 범위"의 판정 기준이다.
   - 검증 통과 시, 같은 트랜잭션에서 아래 순서로 적용한다 (순서가 어긋나면 UNIQUE(holiday_date, source) 위반):
     1. 해당 연도의 source=API 행 전체 삭제
     2. API 응답에 있는 날짜의 (MANUAL, is_holiday=true) 행 **삭제** — 흡수는 전환이 아니라 삭제다. API 행이 대신하므로 name 갱신도 자동 해결된다
     3. 응답 전체를 source=API로 삽입
     4. (MANUAL, is_holiday=false) 행과 COMPANY 행은 손대지 않는다 — 같은 날짜에 API 행과 공존 가능하며, 판정 규칙이 해소한다
     5. 해당 연도의 월 마커 12개 기록 (synced_at 갱신)
   - 검증 실패 시: 적재하지 않고 기존 데이터를 유지한다. 알림 인프라가 아직 없으므로 1단계는 마커의 synced_at 신선도를 리포트 프리플라이트에서 경고로 노출하는 방식으로 실패를 드러낸다(퇴근 미마감 경고와 같은 자리). 관리자 알림은 배치 발송 경로가 생길 때 함께.
3. 계산 규칙:
   - 대상 월의 적재 마커가 없으면 "미적재 — 동기화 API로 적재 후 재시도" 메시지를 반환한다.
   - 날짜별 휴일 판정: (MANUAL, is_holiday=false) 행이 있으면 근무일 > is_holiday=true인 행이 하나라도 있으면 휴일.
   - 부정 오버라이드는 MANUAL 전용이다. COMPANY는 항상 is_holiday=true(회사 지정 휴일)로 제한한다.
4. 관리자 API: 공휴일 추가/삭제(MANUAL/COMPANY 기록), 연도 지정 동기화 트리거(backfill 겸용), 월별 조회. 전부 `@ManagerOnly`. `openapi.yml` 갱신 + `pnpm gen:api` 동반.

## 데이터 모델

- holiday
  - holiday_date
  - name
  - source {API, MANUAL, COMPANY}
  - is_holiday (부정 오버라이드는 MANUAL 전용, COMPANY는 항상 true)
  - UNIQUE (holiday_date, source)
- holiday_month_marker (기존 테이블 유지: marker_month, synced_at)
  - 연 단위 sync_marker 대신 **연간 동기화가 월 마커 12개를 세우는 절충**을 쓴다.
    테이블 교체 마이그레이션이 불필요하고, 계산 경로의 월 단위 검사가 그대로 살며,
    "7월만 재동기화" 같은 부분 재적재도 표현할 수 있다.

## 전환 단계 — greenfield가 아니라 마이그레이션

커밋된 코드(4a5ce8e: 원장 + 월 마커 + 월 단위 동기화)는 위 계획과 형태가 다르다. 필요한 변경:

- [x] **새 Flyway V9** (V8은 적용됨 — 수정 금지): `is_holiday` 컬럼 추가, `source` CHECK 제약에 COMPANY 추가, PK를 `holiday_date` 단독 → `(holiday_date, source)` 유니크로 변경.
  - `Holiday` 엔티티가 복합키(`@IdClass`/`@EmbeddedId`)로 바뀌는 리팩토링 — `equals/hashCode`, `isManual`, merge 함정 회피 로직 전부 영향권.
  - V8 주석과 `Holiday` javadoc의 "is_holiday·COMPANY는 뺐다"는 서술을 V9 주석·javadoc에서 갱신해, 문서와 코드가 서로 반박하는 상태를 남기지 않는다.
  - → `V9__holiday_negative_override_and_company_source.sql` + `HolidayId`(복합키) + `HolidayJudgment`(날짜별 판정 규칙).
    부정 오버라이드 MANUAL 전용 제약은 도메인 생성자와 DDL CHECK 양쪽에 건다.
    `getHolidayDates`가 행 목록이 아니라 판정 결과를 반환하므로 부정 오버라이드가 걸린 날은 휴일에서 빠진다.
    `save()` merge 덮어쓰기 함정은 식별자에 출처가 들어가면서 해소됐다.
- [x] **`applyApiSync` 재작성**: 월 단위 diff + MANUAL 불가침 → 연 단위 범위 교체 + 위 흡수 순서(2절 1~5).
  - 삭제는 벌크 DELETE(`@Modifying(flushAutomatically = true)`)로 한다. 엔티티 삭제에 맡기면
    Hibernate가 flush에서 INSERT를 DELETE보다 먼저 실행해 같은 (날짜, 출처) 키에서 UNIQUE를 위반한다.
  - `applyApiSync`가 요청 연도 밖 날짜를 스스로 거부한다 — 범위 교체는 그 해의 행만 지우므로
    다른 해 날짜가 섞이면 낡은 행 옆에 조용히 얹힌다.
  - `HolidaySyncService.syncYear`는 아직 월 12회 호출로 한 해를 모은다(다음 항목에서 1회로).
  - 커밋 후 상태를 확인하는 `HolidayLedgerServiceIntegrationTest` 추가 — 목으로는 UNIQUE 통과가 증명되지 않는다.
- [x] **`ApiConvertor` 시그니처 변경**: `combineURL(solYear, solMonth)`에서 월 제거(연간 조회), `toApiItem`의 "요청 월 밖 날짜" 검증 → "요청 연도 밖" 검증. 검증 사다리(resultCode / totalCount / 항목 검증)는 유지.
  - 검증 사다리 끝에 **연간 0건 = 실패**를 추가했다. 월 단위 0건은 정상이지만 연간 0건은 미발표 신호다.
  - `countNumberOfStandardWorkingDays`는 연간 응답을 받아 대상 월로 필터한다 — 계산 경로 전환 전까지의 임시 형태.
- [x] **동기화 트리거**(관리자 API 또는 스케줄러)와 **dev `data.sql` 시드**(공휴일 + 월 마커)를 계산 경로 전환 **전에** 만든다 — 없으면 모든 환경에서 리포트가 항상 "미적재"로 거부된다.
  - 둘 다 만들었다: 매일 새벽 정기 동기화(`HolidaySyncScheduler`, 올해~+2년, 연 단위 실패는 건너뜀)와
    `POST /holiday/sync?year=` (`@ManagerOnly`, backfill 겸용, 실패를 삼키지 않고 503).
  - dev 시드는 2026년 공휴일 + 월 마커 12개. 다른 해는 동기화로 채운다.
  - 테스트에서는 `spring.sql.init.mode: never`로 dev 시드를 끄고, `holiday.sync.scheduled.enabled: false`로 cron을 막는다.
  - 남은 4절 항목(공휴일 추가/삭제, 월별 조회 API)은 이 박스 범위 밖이다.
- [x] **계산 경로 원장 전환**: `OverTimeService`가 `ApiConvertor` 실시간 호출 대신 원장을 읽는다. `countNumberOfStandardWorkingDays` / `calculateStandardWorkingMinutes`를 `web` 패키지 밖 서비스/도메인으로 옮기고 `ApiConvertor`는 순수 API 게이트웨이만 남긴다. `@Transactional(readOnly=true)` 부착.
  - 두 메서드는 `domain/working_time/StandardWorkingTime`(record) 하나로 합쳤다 — 소정근로일과
    소정근로시간은 따로 부를 이유가 없는 한 개념이다. `WeekendCalculator`도 `web`에서 같이 옮겼다.
  - 이제 `/overtime`의 503은 `HOLIDAY_DATA_UNAVAILABLE`이 아니라 `HOLIDAY_MONTH_NOT_LOADED`다.
- [x] **감사성**: 매일 동기화는 이미 급여 계산에 쓰인 과거 월도 다시 쓸 수 있다. 마감된 월은 재동기화에서 제외하거나, 최소한 변경 diff를 로그로 남긴다.
  - 둘 중 후자를 택했다. 마감된 월 제외는 "마감" 상태를 표현할 수단(발송 이력 등)이 생긴 뒤의 일이다.
  - `HolidayLedgerDiff`가 적용 전에 추가/삭제/이름변경/수동행 흡수를 계산하고, 지난 달에 걸린 변경은
    WARN으로 올린다(그 달 리포트는 이미 나갔을 수 있으므로 재산정 판단이 필요하다).
  - 감사용 사전 조회 때문에 벌크 DELETE에 `clearAutomatically`가 필요해졌다 — 미리 읽어 둔 엔티티가
    관리 상태로 남으면 재삽입이 merge를 타고 UPDATE로 나가 그 날짜가 조용히 사라진다.
    (통합 테스트로 확인함: 이 옵션을 빼면 2건이 실패한다.)

---

## 전환 이후 남은 것

- [ ] 관리자 공휴일 추가/삭제 API와 월별 조회 (해결 과정 4절 중 동기화 트리거를 뺀 나머지).
- [ ] 배치 프리플라이트에서 `holiday_month_marker.synced_at` 신선도 경고 — 지금은 저장만 하고 아무도 읽지 않는다.
      마커 존재만으로는 "1회 이상 동기화됨"까지만 보장되고 연중 추가된 임시공휴일 누락을 놓친다.
- [ ] `combineURL`이 serviceKey를 인코딩 없이 문자열 연결한다. 디코딩 키의 `+`는 서버에서 공백으로 해석된다. `http://` → `https://`도 함께.
- [ ] dev `data.sql`의 2026년 공휴일 시드는 근사치다. 실제 포털 응답과 대조가 필요하다 —
      특히 2026-07-17 제헌절은 재지정 이후 첫 해라 응답에 실제로 들어 있는지 확인해야 한다.
