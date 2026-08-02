package com.company.officecommute.service.holiday;

import com.company.officecommute.domain.holiday.Holiday;
import com.company.officecommute.domain.holiday.HolidayJudgment;
import com.company.officecommute.domain.holiday.HolidayMonthMarker;
import com.company.officecommute.domain.holiday.HolidayMonthNotLoadedException;
import com.company.officecommute.repository.holiday.HolidayMonthMarkerRepository;
import com.company.officecommute.repository.holiday.HolidayRepository;
import com.company.officecommute.web.HolidayApiItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공휴일 원장의 트랜잭션 경계. 외부 API 호출은 여기 들어오지 않는다.
 */
@Service
public class HolidayLedgerService {

    private final HolidayRepository holidayRepository;
    private final HolidayMonthMarkerRepository holidayMonthMarkerRepository;
    private final Clock clock;

    public HolidayLedgerService(
            HolidayRepository holidayRepository,
            HolidayMonthMarkerRepository holidayMonthMarkerRepository,
            Clock clock
    ) {
        this.holidayRepository = holidayRepository;
        this.holidayMonthMarkerRepository = holidayMonthMarkerRepository;
        this.clock = clock;
    }

    /**
     * API 응답을 해당 월 원장에 반영한다. idempotent — 같은 응답으로 재실행해도 결과가 같다.
     * <p>
     * 동기화가 만지는 건 API 행뿐이다. 사람이 입력한 MANUAL·COMPANY 행은 API가 같은 날짜를
     * 줘도 갱신하지 않고, 응답에 없어도 지우지 않는다. 식별자가 (날짜, 출처)이므로 같은 날짜에
     * API 행을 새로 넣어도 기존 MANUAL 행을 덮어쓰지 않고 공존한다 — 최종 판정은
     * {@link HolidayJudgment}가 내린다.
     * <p>
     * 공휴일이 0건인 달도 마커를 남긴다 — 마커가 "정상적으로 0개인 달"과 "적재 안 된 달"을 구분한다.
     */
    @Transactional
    public void applyApiSync(YearMonth month, List<HolidayApiItem> apiItems) {
        Map<LocalDate, String> apiNameByDate = new HashMap<>();
        for (HolidayApiItem item : apiItems) {
            apiNameByDate.putIfAbsent(item.date(), item.name());
        }

        List<Holiday> existingRows = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
                month.atDay(1), month.atEndOfMonth());
        for (Holiday row : existingRows) {
            if (!row.isFromApi()) {
                continue;
            }
            String apiName = apiNameByDate.remove(row.getHolidayDate());
            if (apiName == null) {
                holidayRepository.delete(row);
            } else if (!apiName.equals(row.getName())) {
                row.updateNameFromApi(apiName);
            }
        }

        for (Map.Entry<LocalDate, String> newHoliday : apiNameByDate.entrySet()) {
            holidayRepository.save(Holiday.fromApi(newHoliday.getKey(), newHoliday.getValue()));
        }

        holidayMonthMarkerRepository.save(HolidayMonthMarker.of(month, clock.instant()));
    }

    /**
     * 해당 월에 실제로 휴일인 날짜를 반환한다. 적재 마커가 없는 달은 "공휴일 0개"가 아니라
     * 완전성을 보장할 수 없는 상태이므로 값 대신 예외로 계산을 거부한다.
     * <p>
     * 행이 있는 날짜가 곧 결과는 아니다 — 부정 오버라이드가 걸린 날은 행이 있어도 근무일이다.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> getHolidayDates(YearMonth month) {
        if (!holidayMonthMarkerRepository.existsByMonth(month)) {
            throw new HolidayMonthNotLoadedException(month);
        }
        return HolidayJudgment.holidayDatesOf(
                holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(month.atDay(1), month.atEndOfMonth()));
    }
}
