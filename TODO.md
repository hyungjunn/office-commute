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

- [ ] `holiday(holiday_date PK, name, source[API|MANUAL], is_holiday)` — 갯수가 아니라 **날짜** 단위.
      갯수만 저장하면 주말 중복 판정·대체공휴일·향후 일별 계산을 할 수 없다 (파생 원칙과도 일치)
- [ ] 수동 오버라이드가 재동기화에 덮이지 않게: 동기화는 `source=MANUAL` 행을 건드리지 않는다.
      `is_holiday=false, source=MANUAL`은 "API가 공휴일이라 했으나 아님"이라는 부정 오버라이드
- [ ] **"공휴일 0개인 달"과 "아직 적재 안 된 달"의 구분** — 4월·11월은 정상적으로 0개다.
      월 단위 적재 마커가 없으면 1번에서 잡은 silent fail-open이 DB로 이사한다.
      마커 없는 달은 계산을 거부한다
- [ ] 관리자 API (전부 `@ManagerOnly`): 월별 조회 / 추가 / 삭제 / 동기화 트리거
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

**전제: 배치도 메일 발송도 아직 없다.** 스케줄러(`@Scheduled`)와 메일 의존성이
프로젝트에 존재하지 않으므로, 이 절의 항목들은 기존 코드의 수정이 아니라
발송 경로를 새로 만드는 작업이다. 리포트 생성부(`OverTimeReportService`)는 준비돼 있다.

- [ ] 대상 월 공휴일 적재/확인 여부 (2번 의존)
- [ ] **퇴근 미처리 기록** — `workEndTime IS NULL`이면 `workingMinutes=0`으로 SUM에 들어간다.
      1일 오전 배치면 전월 말일에 퇴근을 찍지 않은 직원이 그대로 0분 처리된다
- [ ] 배치는 `StreamingResponseBody` 컨트롤러를 타지 말고 서비스 계층을 직접 호출한다.
      실패 시 부분 파일이 나가지 않도록 임시 파일에 다 쓴 뒤 첨부한다
- [ ] **중복 발송 방지** — 배치 재시도나 수동 재실행 시 대표가 같은 리포트를 여러 번 받는다.
      발송 이력 테이블에 `(year_month)` 유니크를 걸어 한 달에 한 번만 나가게 한다
- [ ] **조용한 실패 차단** — 스케줄러 안에서 예외가 삼켜지면 아무도 모르는 채로 그 달 리포트가 사라진다.
      실패는 근태 관리자에게 알림으로 드러나야 한다 (메일이 안 온 것과 구분이 안 되면 안 된다)

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
- [ ] `HolidayResponse.Item.setLocDate` 오타 (필드는 `locdate`)
