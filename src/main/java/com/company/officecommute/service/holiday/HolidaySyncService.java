package com.company.officecommute.service.holiday;

import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.web.ApiConvertor;
import com.company.officecommute.web.HolidayApiItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 공휴일 API를 원장에 적재하는 오케스트레이터. API 호출이 트랜잭션 밖에서 일어나도록
 * 의도적으로 트랜잭션을 갖지 않는다 — 트랜잭션 경계는 {@link HolidayLedgerService}에 있다.
 * 호출 실패 시 예외가 그대로 전파되고 원장과 마커는 변하지 않는다.
 */
@Service
public class HolidaySyncService {

    /**
     * 올해부터 +2년까지 적재한다. 연말에 다음 해 리포트를 뽑을 수 있어야 하고, 여유분 1년은
     * 동기화가 며칠 멈춰도 원장이 마르지 않게 한다.
     */
    private static final int YEARS_AHEAD = 2;

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);

    private final ApiConvertor apiConvertor;
    private final HolidayLedgerService holidayLedgerService;
    private final Clock clock;

    public HolidaySyncService(
            ApiConvertor apiConvertor,
            HolidayLedgerService holidayLedgerService,
            Clock clock
    ) {
        this.apiConvertor = apiConvertor;
        this.holidayLedgerService = holidayLedgerService;
        this.clock = clock;
    }

    /**
     * 한 해를 통째로 동기화하고 적재 건수를 반환한다. 원장 적용이 연 단위 범위 교체라 적재 단위도
     * 연이어야 한다 — 월 단위로 부르면 첫 달이 그 해의 나머지 API 행을 전부 지운다.
     * <p>
     * 실패는 그대로 전파된다. 호출자가 관리자라면 실패를 알아야 하고, 원장은 손대지 않았으므로
     * 이전 값이 그대로 살아 있다.
     */
    public int syncYear(Year year) {
        List<HolidayApiItem> apiItems = apiConvertor.fetchHolidays(year);
        holidayLedgerService.applyApiSync(year, apiItems);
        log.info("공휴일 원장 동기화 완료: year={}, API 항목 {}건", year, apiItems.size());
        return apiItems.size();
    }

    /**
     * 매일 동기화가 도는 범위 — 올해부터 +2년까지. 성공한 연도만 반환한다.
     * <p>
     * 한 해의 실패로 나머지를 멈추지 않는다. +2년차는 아직 공휴일이 발표되지 않아 0건이 오는 것이
     * <b>정상적인 실패</b>이기 때문이다. 그 해를 적재하지 않고 마커도 세우지 않는 것이 곧
     * "원장이 커버하는 범위"의 표현이고, 계산 경로는 마커 없는 달을 거부해 스스로를 지킨다.
     * <p>
     * 올해가 실패하는 건 이야기가 다르지만 여기서 할 수 있는 일은 없다 — 원장의 이전 값이 남고,
     * 낡음은 마커의 synced_at 신선도 검사가 드러낸다. 알림 인프라가 생기면 여기서 관리자를 부른다.
     */
    public List<Year> syncUpcomingYears() {
        Year currentYear = Year.now(clock);
        List<Year> syncedYears = new ArrayList<>();

        for (int offset = 0; offset <= YEARS_AHEAD; offset++) {
            Year year = currentYear.plusYears(offset);
            try {
                syncYear(year);
                syncedYears.add(year);
            } catch (HolidayDataUnavailableException e) {
                log.warn("공휴일 동기화를 건너뜁니다. 이 해는 원장에 적재되지 않고 기존 값이 유지됩니다. "
                        + "year={}, reason={}", year, e.getMessage());
            }
        }

        if (syncedYears.isEmpty()) {
            log.error("공휴일 동기화가 {}~{}년 전부 실패했습니다. 원장이 갱신되지 않았습니다.",
                    currentYear, currentYear.plusYears(YEARS_AHEAD));
        }
        return syncedYears;
    }
}
