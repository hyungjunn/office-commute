# TODO — 초과근무 리포트 신뢰성

매월 1일 오전 배치로 전월 초과근무를 집계해 엑셀로 만들어 대표 이메일로 발송하는 것이 최종 목표.
급여 근거 자료이므로 "조용히 틀린 값"이 "리포트가 안 나감"보다 훨씬 비싸다는 원칙으로 우선순위를 정한다.

관련 경로:
`OverTimeController` → `OverTimeReportService` → `OverTimeService.calculateOverTime`
→ `CommuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween` + `ApiConvertor.countNumberOfStandardWorkingDays`

---

## 1. 공휴일 API 응답 검증 — silent fail-open 차단 ✅ 완료

`ApiConvertor.getHolidays()`가 응답 내용을 검증하지 않아, 키 오류·트래픽 초과 등을
**HTTP 200 + XML 에러**로 받으면 공휴일 0개로 계산하고 로그에 "호출 성공"을 남겼다.
→ 소정근로일 과대 → 소정근로시간 과대 → **전 직원 초과근무 과소 → 임금 미지급**.

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

## 2. 공휴일 DB 원장화 + 관리자 수동 오버라이드

지금은 매월 1일 오전의 급여 계산이 공공데이터포털 가용성에 직접 매달려 있다(재시도 없음, 3s/5s 타임아웃).
캐시가 아니라 **DB를 원장(system of record)으로 승격**시키고 외부 API를 적재 도구로 격하시킨다.
계산 경로는 DB만 읽으므로 결정론적이고 오프라인에서도 동작한다.
`V6__drop_holiday_cache_tables.sql`로 지웠던 캐시의 재도입이 아니라 다른 설계다.

- [x] `holiday(holiday_date PK, name, source[API|MANUAL])` — 갯수가 아니라 **날짜** 단위.
      갯수만 저장하면 주말 중복 판정·대체공휴일·향후 일별 계산을 할 수 없다 (파생 원칙과도 일치)
      → `V8__create_holiday_ledger.sql` + `domain/holiday/{Holiday,HolidaySource}` + `HolidayRepository`.
      **결정: `is_holiday` 부정 오버라이드("공휴일이지만 아님")는 뺐다** — 확정된 법정 공휴일이
      사후 취소된 전례가 사실상 없고(제헌절 제외도 연도 단위 법 개정이라 해당 연도 응답에
      안 나타나는 방식), 남는 용도는 API 데이터 오류 보정뿐인데 이는 실제 발생이 확인되면
      도입한다(YAGNI). 행이 곧 공휴일이다. 관리자 등록은 `Holiday.manual(date, name)`.
      **주의**: PK가 할당식이라 `save()`가 merge로 동작해 같은 날짜를 조용히 덮어쓴다
      (`HolidayRepositoryTest.saveOverwritesSameDate`). 동기화는 `save()`에 기대지 말고
      기존 행을 먼저 조회해 MANUAL 여부를 판단해야 한다
- [x] 수동 오버라이드가 재동기화에 덮이지 않게: 동기화는 `source=MANUAL` 행을 건드리지 않는다
      → `HolidaySyncService`(API 호출, 트랜잭션 밖) + `HolidayLedgerService.applyApiSync`(트랜잭션 경계).
      MANUAL 행은 갱신·삭제 모두 건너뛰고, 같은 날짜의 API 항목도 저장하지 않는다(merge 함정 회피).
      API 행은 이름 갱신(dirty checking)·응답에서 사라지면 삭제. idempotent.
      관리자가 잘못 넣은 MANUAL 행의 취소는 그냥 삭제 — API에 없는 날짜라 동기화가 되살리지 않는다
- [x] **"공휴일 0개인 달"과 "아직 적재 안 된 달"의 구분** — 4월·11월은 정상적으로 0개다.
      월 단위 적재 마커가 없으면 1번에서 잡은 silent fail-open이 DB로 이사한다.
      마커 없는 달은 계산을 거부한다
      → `holiday_month_marker(marker_month PK, synced_at)` + `HolidayMonthMarker`.
      마커는 **동기화 성공만이** 세운다(0건인 달도 세움). 수동 공휴일 추가는 그 달의 완전성을
      보장하지 않으므로 마커를 세우지 않는다. `HolidayLedgerService.getHolidayDates`가 마커 없는
      달에 `HolidayMonthNotLoadedException`(503 `HOLIDAY_MONTH_NOT_LOADED`)을 던진다
- [ ] 관리자 API (전부 `@ManagerOnly`): 월별 조회 / 추가 / 삭제 / 동기화 트리거
- [ ] **계산 경로의 원장 전환** — `OverTimeService`가 `ApiConvertor` 실시간 호출 대신
      `HolidayLedgerService.getHolidayDates`를 읽게 바꾼다. 이게 이 섹션의 최종 목표
      ("계산 경로는 DB만 읽는다")를 실제로 완성하는 단계다.
      **순서 주의**: 반드시 동기화 트리거(위 관리자 API 또는 정기 동기화)가 생긴 뒤에 —
      원장을 채울 수단이 없는 채로 전환하면 마커 있는 달이 없어 모든 환경에서
      리포트가 항상 거부된다. 전환 시 `@Transactional(readOnly=true)`도 함께 (6번 첫 항목 해소)
- [ ] 정기 idempotent 재동기화 — 대선일 같은 사후 지정도 확정되면 API에 반영되므로
      정기 동기화가 수작업 대부분을 대신하고, 수동 API는 긴급 창구로 남는다
- [ ] **2026-08-01 배치 전 실측 확인**: 2026년 7월분이 제헌절 재지정 이후 첫 7월 리포트다.
      포털 응답에 `20260717` 항목이 실제로 들어 있는지 눈으로 확인할 것. 누락되어 있으면
      소정근로일이 1일 과대 계산되어 전 직원 초과근무가 8시간씩 과소 집계된다.
      (반영이 늦는 이런 상황이 수동 오버라이드가 필요한 대표 사례다)
- [ ] 외부 호출을 `ApiConvertor`(계산 로직을 가진 `web` 패키지 클래스, 층 위반)에서 분리
- [ ] `combineURL`이 serviceKey를 인코딩 없이 문자열 연결한다. 디코딩 키의 `+`는
      서버에서 공백으로 해석되어 인증 실패한다(키 문제의 흔한 원인). `new URI(...)` 파싱 실패도 가능
- [ ] `http://` → `https://`

## 3. 소정근로시간 개인화 — 실질 임금 오류

현재 소정근로시간(근무일 × 8h)을 전 직원에게 일괄 적용한다.

- [ ] **연차가 초과근무를 잡아먹는다.** `CommuteHistory.registerAnnualLeave`는 `workingMinutes=0`인데
      소정근로시간은 차감되지 않는다 → 연차 1일 쓴 직원은 8시간 결손을 깔고 시작하므로
      실제로 야근해도 초과근무 0으로 집계된다
- [ ] 월 중 입사자도 같은 방식으로 왜곡된다 (`Employee.workStartDate` 활용)
- [ ] `Employee`에 퇴사 상태가 없어 `findAllWithTeam()`이 퇴사자까지 0으로 리포트에 싣는다

## 4. 배치 프리플라이트 체크

잘못된 급여 자료가 대표에게 도착하는 것이 메일이 하루 늦는 것보다 비싸다.
문제가 있으면 대표 메일 대신 근태 관리자에게 경고를 보낸다.

- [ ] 대상 월 공휴일 적재/확인 여부 (2번 의존)
- [ ] **퇴근 미처리 기록** — `workEndTime IS NULL`이면 `workingMinutes=0`으로 SUM에 들어간다.
      1일 오전 배치면 전월 말일에 퇴근을 찍지 않은 직원이 그대로 0분 처리된다
- [ ] 배치는 `StreamingResponseBody` 컨트롤러를 타지 말고 서비스 계층을 직접 호출한다

## 5. 법정 산정 방식 정합

- [ ] **월 합계 상계는 근로기준법 산정 방식이 아니다.** 가산은 1일 8시간 초과 / 1주 40시간 초과 기준.
      어떤 날 4h 야근하고 다른 날 4h 조퇴하면 현재는 0이지만 법적으로는 4h 가산수당 지급 의무가 남는다.
      `DailyWorkDuration`이 이미 있어 일별 계산 기반은 갖춰져 있다
- [ ] 직원별 통상시급 (현재 `OverTimeReportService.HOURLY_ORDINARY_WAGE = 15000` 전 직원 하드코딩)
- [ ] 휴일근로(8h 이내 1.5 / 초과 2.0)·야간근로(22~06시 +0.5)가 단일 `1.5`로 뭉쳐 있다

## 6. 마이너

- [ ] `OverTimeService.calculateOverTime`에 `@Transactional(readOnly = true)` 부재 —
      두 쿼리 사이 비일관 스냅샷 가능. 단, 붙일 때는 외부 API 호출을 트랜잭션 밖으로 빼야 한다
      (2번 완료 후에는 자연히 해소)
- [ ] 외부 API 타임아웃을 `application-*.yml`로 분리 (`RestTemplateConfig`의 기존 TODO)
- [x] `HolidayResponse.Item.setLocDate` 오타 (필드는 `locdate`) → `setLocdate`로 수정
