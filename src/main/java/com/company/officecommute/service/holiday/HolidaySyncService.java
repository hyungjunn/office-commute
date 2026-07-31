package com.company.officecommute.service.holiday;

import com.company.officecommute.web.ApiConvertor;
import com.company.officecommute.web.HolidayApiItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * 외부 공휴일 API를 원장에 적재하는 오케스트레이터. API 호출이 트랜잭션 밖에서 일어나도록
 * 의도적으로 트랜잭션을 갖지 않는다 — 트랜잭션 경계는 {@link HolidayLedgerService}에 있다.
 * 호출 실패 시 예외가 그대로 전파되고 원장과 마커는 변하지 않는다.
 */
@Service
public class HolidaySyncService {

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);

    private final ApiConvertor apiConvertor;
    private final HolidayLedgerService holidayLedgerService;

    public HolidaySyncService(ApiConvertor apiConvertor, HolidayLedgerService holidayLedgerService) {
        this.apiConvertor = apiConvertor;
        this.holidayLedgerService = holidayLedgerService;
    }

    public void syncMonth(YearMonth month) {
        List<HolidayApiItem> apiItems = apiConvertor.fetchHolidays(month);
        holidayLedgerService.applyApiSync(month, apiItems);
        log.info("공휴일 원장 동기화 완료: month={}, API 항목 {}건", month, apiItems.size());
    }
}
