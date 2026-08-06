# TODO — 초과근무 리포트 신뢰성

매월 1일 오전 배치로 전월 초과근무를 집계해 엑셀로 만들어 대표 이메일로 발송하는 것이 최종 목표.
급여 근거 자료이므로 "조용히 틀린 값"이 "리포트가 안 나감"보다 훨씬 비싸다는 원칙으로 우선순위를 정한다.

관련 경로:
`OverTimeController` → `OverTimeReportService` → `OverTimeService.calculateOverTime`
→ `CommuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween`
+ `StandardWorkingTimeService.countNumberOfStandardWorkingDays` → `HolidayApiClient.getHolidays`

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

## 3. 소정근로시간 개인화 — 실질 임금 오류

현재 소정근로시간(근무일 × 8h)을 전 직원에게 일괄 적용한다.

- [ ] **연차가 초과근무를 잡아먹는다.** `CommuteHistory.registerAnnualLeave`는 `workingMinutes=0`인데
      소정근로시간은 차감되지 않는다 → 연차 1일 쓴 직원은 8시간 결손을 깔고 시작하므로
      실제로 야근해도 초과근무 0으로 집계된다
- [ ] 월 중 입사자도 같은 방식으로 왜곡된다 (`Employee.workStartDate` 활용)
- [ ] `Employee`에 퇴사 상태가 없어 `findAllWithTeam()`이 퇴사자까지 0으로 리포트에 싣는다

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

- [ ] **월 합계 상계는 근로기준법 산정 방식이 아니다.** 가산은 1일 8시간 초과 / 1주 40시간 초과 기준.
      어떤 날 4h 야근하고 다른 날 4h 조퇴하면 현재는 0이지만 법적으로는 4h 가산수당 지급 의무가 남는다.
      `DailyWorkDuration`이 이미 있어 일별 계산 기반은 갖춰져 있다
- [ ] 직원별 통상시급 (현재 `OverTimeReportService.HOURLY_ORDINARY_WAGE = 15000` 전 직원 하드코딩)
- [ ] 휴일근로(8h 이내 1.5 / 초과 2.0)·야간근로(22~06시 +0.5)가 단일 `1.5`로 뭉쳐 있다

## 6. 마이너

- [ ] `OverTimeService.calculateOverTime`에 `@Transactional(readOnly = true)` 부재 —
      두 쿼리 사이 비일관 스냅샷 가능. 붙일 때는 외부 API 호출(`StandardWorkingTimeService`
      경유)을 트랜잭션 밖으로 빼야 한다
- [ ] 외부 API 타임아웃을 `application-*.yml`로 분리 (`RestTemplateConfig`의 기존 TODO)
- [x] `HolidayResponse.Item.setLocDate` 오타 수정 (`setLocdate`, 필드와 일치 — 클라이언트
      분리에 딸려 처리)
