# TODO — 초과근무 리포트 신뢰성

매월 1일 오전 배치로 전월 초과근무를 집계해 엑셀로 만들어 대표 이메일로 발송하는 것이 최종 목표.
급여 근거 자료이므로 "조용히 틀린 값"이 "리포트가 안 나감"보다 훨씬 비싸다는 원칙으로 우선순위를 정한다.

관련 경로:
`OverTimeController` → `OverTimeReportService` → `OverTimeService.calculateOverTime`
→ `CommuteHistoryRepository.findDailyWorkingMinutesByWorkDateBetween` + `MonthlyOverTimeCalculator`
+ `HolidayApiClient.getHolidays` (휴일근로 분류용 — 대상 월, 첫 주가 걸치면 전월도)

---

## 1. 공휴일 API 응답 검증 — silent fail-open 차단 ✅ 완료

`ApiConvertor.getHolidays()`가 응답 내용을 검증하지 않아, 키 오류·트래픽 초과 등을
**HTTP 200 + XML 에러**로 받으면 공휴일 0개로 계산하고 로그에 "호출 성공"을 남겼다.
→ 소정근로일 과대 → 소정근로시간 과대 → **전 직원 초과근무 과소 → 임금 미지급**.

> **"HTTP 200 + XML 에러"가 재현이 안 된다는 지적에 대해** (2026-08-08 실측):
> 게이트웨이 인증류 오류는 현재 상태 코드가 제대로 붙어서 온다(잘못된 키 403,
> 키 누락 401 — 이 경우 RestTemplate이 예외를 던져 검증 없이도 fail-closed).
> 그럼에도 이 서술이 유효한 이유: ① 포털의 과거·타 서비스에서 널리 확인된 동작이고
> ② 정상 envelope + `resultCode != 00`(예: 트래픽 초과 22)은 뒷단 서비스 응답이라
> **지금도 HTTP 200으로 온다** — 쿼터를 소진해야 재현되므로 실측에 안 보일 뿐이다.
> 두 모양 모두 `HolidayApiClientWireTest`가 200 케이스로 고정하고 있다.

- [x] `header/resultCode` 검증 (`00` 외에는 실패 처리, `resultMsg`를 로그에 남긴다)
- [x] `isHoliday` 필터 도입 후 철회 — `getRestDeInfo`는 **공휴일 정보조회** 전용
      엔드포인트라(국경일은 `getHoliDeInfo`) 응답 항목은 정의상 모두 공휴일이고
      `isHoliday`는 항상 `Y`인 잉여 필드다. 필터는 실재하지 않는 문제를 막으면서
      플래그가 흔들리면 리포트를 전면 중단시키는 새 위험만 추가했으므로 걷어냈다.
      **연도별 공휴일 지정 판단은 API의 책임**이다(제헌절은 2026년 재지정 이후에만
      응답에 나타난다). 날짜도 플래그도 코드에 박지 않는다.
      → 의존하는 계약: "이 엔드포인트 응답의 모든 항목은 공휴일이다"
- [x] `totalCount` == 수신 건수 검증(`validateResponseCount`), `numOfRows=100` 고정.
      정상 0건의 계약은 **items 없음 + `totalCount=0`**이므로 `totalCount` 누락은 정상이 아니라
      실패로 다룬다 — 없으면 응답의 완전성을 확인할 수 없고, 잘림도 감지하지 못한다
- [x] 구체적 사유가 담긴 `HolidayDataUnavailableException`이 바깥 `catch (Exception)`에
      덮여 일반 메시지로 바뀌던 문제 수정

## 2. 공휴일 — 순수 라이브 호출 (fail-closed), 저장 계층 없음

**저장 계층 전체(원장·동기화·MANUAL 오버라이드)를 도입하지 않는다.** 계산은 매번 외부 API를
라이브로 호출하고, 실패는 1번의 검증이 명시적 예외("현재 이용 불가")로 드러낸다 —
온디맨드 조회는 사람이 다시 누르면 되고, 배치는 재시도(4번)가 흡수한다.

근거 — **후향 집계 구조가 "API가 200인데 내용이 틀리는" 위험을 소거한다**:
- 리포트는 전월, 즉 지나간 날짜만 합산한다. 임시공휴일·선거일은 항상 발생일 이전에
  공표되고 포털 반영 지연은 며칠 수준이므로, 발생 후 최대 한 달 뒤인 리포트 시점에는
  확정된 사실이다
- 소급 지정(지나간 날을 나중에 공휴일로)은 제도상 존재하지 않는다
- 마지막 불확실성이었던 "재지정 첫 해 제헌절의 포털 반영"은 실측으로 해소 (아래)

- [x] **제헌절 실측 확인 완료 (2026-08-03)**: `solYear=2026&solMonth=07` 응답에
      `20260717 제헌절` 포함 (`resultCode=00`, `totalCount=1`). 재지정이 포털에 반영돼
      있음을 확인 — 7월 리포트는 현재 코드로 올바르게 계산된다
- [x] 스키마·도메인 정리(한 커밋으로 — 따로 하면 dev 부팅이 깨진다):
      `V10__drop_holiday_tables.sql`로 `holiday`·`holiday_sync_marker` drop
      (적용된 V8·V9는 수정 불가 — V6이 캐시 테이블을 지운 것과 같은 방식),
      `domain/holiday` 패키지(`Holiday`·`HolidaySource`·`HolidaySyncMarker`) 삭제
      (main+test — 참조하는 다른 코드 없음을 확인), `data.sql`의 공휴일·마커 시드 전부 제거
- [x] 외부 호출을 `ApiConvertor`(계산 로직을 가진 `web` 패키지 클래스, 층 위반)에서 분리:
      `web/HolidayApiClient`(HTTP 호출 + 응답 검증 + `Set<LocalDate>` 변환)와
      `service/overtime/StandardWorkingTimeService`(소정근로일·소정근로시간 계산)로.
      `ApiProperties` 인터페이스·`PublicDataApi`는 `HolidayApiProperties`로 대체.
      테스트도 같은 경계로 분리 — `@SpringBootTest`였던 클라이언트 테스트를 Mockito
      단위 테스트로 내림(testing.md의 narrowest slice)
- [x] 클라이언트 재작성에 URL 조립 수정 포함 — 구 `combineURL`은 serviceKey를 인코딩 없이
      문자열 연결했고, 동작한 이유는 `.env`의 키가 **이미 URL 인코딩된 형태**라서였다
      (실측: 같은 키를 `--data-urlencode`로 재인코딩하면 인증 실패 — 이중 인코딩).
      → 계약을 코드에 명시: `HolidayApiProperties.serviceKey`는 인코딩 키를 그대로 보관,
      `UriComponentsBuilder` + `build(true)`로 재인코딩 없이 조립.
      재인코딩 회귀는 `HolidayApiClientTest`의 URI 검증 테스트가 고정한다
- [x] `http://` → `https://` (세 프로파일 yml의 `PUBLIC_API_URL` 기본값)

### 철회 기록 (재도입 검토 시 읽을 것)

- **원장 + 매일 동기화 + 연 마커 + 신선도 경고** (V8·V9 시점 설계, 2026-08 철회):
  요구사항(월별 조회 + 매월 1일 배치 메일)이 요구하는 것은 배치+발송이지 공휴일 저장이
  아니다. 원장은 본질적으로 "미리 해두는 재시도"이고, 같은 가용성은 배치 시점 재시도가
  더 싸게 준다. "메일이 하루 늦는" 비용은 이 문서의 원칙상 이미 수용된 쪽이다
- **MANUAL 오버라이드** (2026-08-03 철회): "200인데 내용이 틀린" 경우의 교정 레버로
  검토했으나, 후향 집계에서는 그 경우가 구조적으로 소거되고 제헌절 실측으로 마지막
  근거도 사라졌다. **재진입 조건: 지나간 날짜에 대해 포털이 실제로 틀린 첫 사고** —
  그때 git 이력의 V8 설계(`V8__holiday_ledger.sql` + `domain/holiday`)를 복원한다
- **COMPANY 소스(회사 지정 휴일)**: 소정근로일에서 뺀다는 급여 정책이 확정된 요구가
  아니라 보류. 필요해지면 저장 계층 재도입과 함께 그때 설계한다

## 3. 리포트 대상자 정합

(구 제목: "소정근로시간 개인화 — 실질 임금 오류". 5번에서 월 소정근로시간 차감 방식 자체가
제거되면서 아래 두 왜곡은 구조적으로 해소됐다 — 기준선이 "실근로 일 8h·주 40h"로 바뀌어
근무하지 않은 날이 결손을 만들지 않는다.)

- [x] **연차가 초과근무를 잡아먹던 문제** — 소정근로시간 차감이 사라져 해소. 연차는 실근로 0으로
      주 40h 산정에 들어가지 않을 뿐이고, 연차 주간의 야근은 일 8h 초과로 그대로 잡힌다
      (`MonthlyOverTimeCalculatorTest.annualLeaveWeekStillAccruesDailyExcess` 고정)
- [x] 월 중 입사자 왜곡 — 같은 이유로 해소. 입사 전 날짜는 기록이 없을 뿐 결손이 아니다
- [x] `Employee`에 퇴사 상태가 없어 `findAllWithTeam()`이 퇴사자까지 0으로 리포트에 싣던 문제
      (2026-08-10, ADR 2 Step 0.3 선행 조치). `workEndDate`(nullable 퇴사일) 도입 —
      boolean이 아닌 날짜인 이유: 월 중 퇴사자의 그 달 초과근무는 지급 대상이라
      "월과 재직 기간이 겹침"을 표현해야 한다. 리포트 대상 = 재직 기간이 월 경계(1일~말일)와
      겹치는 직원(`findAllWithTeamEmployedBetween`) — 스필오버 주는 대상 판정에 쓰지 않는다
      (전월 말 퇴사자의 그 주 근무는 전월 리포트가 이미 집계). 입사 예정자도 같은 필터로 제외.
      기록은 `PUT /employee/{id}/retirement` (ManagerOnly, null = 취소)
- [ ] 퇴사자 로그인·출퇴근 등록 차단 — `workEndDate`가 지나도 로그인과 출근 체크가 가능하다
      (리포트에서만 제외). `AuthInterceptor`는 세션만 읽고 DB를 안 타므로 가장 싼 지점은
      `authenticate()`의 로그인 거부. "오늘"의 기준 시간대(직원 timezone? KST?)와
      퇴사일 당일 마지막 퇴근 처리 허용 정책을 정한 뒤 별도 작업으로

## 4. 매월 1일 배치 — 이번 작업의 본체

잘못된 급여 자료가 대표에게 도착하는 것이 메일이 하루 늦는 것보다 비싸다.
문제가 있으면 대표 메일 대신 근태 관리자에게 경고를 보낸다.

**전제: 배치도 메일 발송도 아직 없다.** 스케줄러(`@Scheduled`)와 메일 의존성이
프로젝트에 존재하지 않으므로, 이 절의 항목들은 기존 코드의 수정이 아니라
발송 경로를 새로 만드는 작업이다. 리포트 생성부(`OverTimeReportService`)는 준비돼 있다.

- [ ] 스케줄러 도입(프로젝트 첫 도입): `@EnableScheduling` + `@Scheduled`에
      cron `zone = "Asia/Seoul"` 명시. "전월"은 주입된 `Clock`에서 파생 —
      `LocalDate.now()` 직접 호출 금지
- [ ] 배치는 `StreamingResponseBody` 컨트롤러를 타지 말고 서비스 계층을 직접 호출한다.
      실패 시 부분 파일이 나가지 않도록 임시 파일에 다 쓴 뒤 첨부한다
- [ ] **발송 이력 테이블 `UNIQUE(year_month)`** — 중복 발송 방지의 핵심.
      배치 재시도와 수동 재실행이 겹쳐도 대표는 한 달에 한 번만 받는다
- [ ] **재시도가 원장을 대체한다**: 1일 오전 시도가 실패하면(공휴일 API 다운 포함)
      몇 시간 간격으로 1~3일 재시도. 발송 이력 유니크 덕에 성공 시 자동 중단, 멱등.
      공휴일 프리플라이트는 별도 항목이 아니다 — 라이브 호출이 fail-closed라
      데이터 문제가 곧 배치 실패로 드러나고, 재시도 대상이 된다
- [ ] **조용한 실패 차단** — 재시도까지 소진한 최종 실패는 근태 관리자에게 알림으로
      드러나야 한다 (메일이 안 온 것과 구분이 안 되면 안 된다)
- [x] **퇴근 미처리 기록 — 탐지·노출 완료.** `workEndTime IS NULL`이면 `workingMinutes=0`으로 SUM에 들어간다.
      1일 오전 배치면 전월 말일에 퇴근을 찍지 않은 직원이 그대로 0분 처리된다.
      `countByWorkDateBetweenAndWorkEndTimeIsNull`로 건수를 세어 `OverTimeReport`에 싣고,
      시트 첫 행에 경고로 남긴다(연차는 `workEndTime`이 채워지므로 오탐 없음).
      → 남은 것은 **라우팅**: 건수가 0이 아닐 때 대표 발송을 막고 근태 관리자에게 보낼지 여부.
      발송 경로가 생긴 뒤 결정한다

## 5. 법정 산정 방식 정합

- [x] **월 합계 상계 → 주 단위 법정 산정으로 교체** (2026-08-09, `MonthlyOverTimeCalculator`).
      1주(월~일)의 연장근로 = Σ일별 max(0, 실근로−8h) + max(0, Σ일별 min(실근로, 8h) − 40h) —
      두 항은 겹치지 않아 이중가산 없음. 확정한 정책:
      - **실근로 기준**: 연차는 주 40h 산정에 불포함, 기준선도 40h 유지 (법정 최소.
        연차를 8h 근로로 간주하는 우대 정책은 채택하지 않음)
      - **월 경계에 걸친 주**: 일별 초과분은 그 날이 속한 달, 주 40h 잔여분은 주가 끝나는 달에
        귀속 — 매월 1일 배치 시점에 걸친 주가 아직 끝나지 않아 데이터 가용성상으로도 강제되는 선택.
        이를 위해 조회 범위를 "월 1일이 속한 주의 월요일"까지 확장 (`OverTimeService`)
      - 산정 근거 사례는 전부 `MonthlyOverTimeCalculatorTest`가 고정
- [x] **휴일근로 분리** (같은 커밋): 일요일(주휴일)·공휴일 근무는 주 40h 산정 기반에서 제외하고
      별도 트랙으로 — 8h 이내 1.5 / 초과 2.0 (제56조②, 연장가산과 중복 없음).
      토요일은 무급휴무일로 보아 휴일이 아니며 주 40h 초과 경로로 잡힌다.
      응답·엑셀·`openapi.yml`에 연장/휴일(이내·초과) 구분 필드 추가.
      `StandardWorkingTimeService`(소정근로일 개념)·`WeekendCalculator`·월 SUM 쿼리는 퇴역
- [ ] 직원별 통상시급 (현재 `OverTimeReportService.HOURLY_ORDINARY_WAGE = 15000` 전 직원 하드코딩)
- [ ] 야간근로(22~06시 +0.5) 미반영 — `workStartTime`/`workEndTime` Instant가 있어 계산 가능하지만
      휴게시간 미모델링과 함께 봐야 한다. 주 시작 요일(월요일)은 취업규칙 확정 사항으로 코드와 일치 필요

## 6. 마이너

- [ ] `OverTimeService.calculateOverTime`에 `@Transactional(readOnly = true)` 부재 —
      두 쿼리 사이 비일관 스냅샷 가능. 붙일 때는 외부 API 호출(`HolidayApiClient`)을
      트랜잭션 밖으로 빼야 한다
- [ ] 외부 API 타임아웃을 `application-*.yml`로 분리 (`RestTemplateConfig`의 기존 TODO)
- [x] `HolidayResponse.Item.setLocDate` 오타 수정 (`setLocdate`, 필드와 일치 — 클라이언트
      분리에 딸려 처리)
