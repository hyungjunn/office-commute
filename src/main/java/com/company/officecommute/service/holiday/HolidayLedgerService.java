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
import java.time.Year;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 공휴일 원장의 트랜잭션 경계. 외부 API 호출은 여기 들어오지 않는다.
 */
@Service
public class HolidayLedgerService {

    private static final int MONTHS_PER_YEAR = 12;

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
     * 한 해의 API 응답을 원장에 통째로 반영한다. 월 단위 diff가 아니라 <b>연 단위 범위 교체</b>다 —
     * 해당 연도의 API 행을 지우고 응답 전체를 다시 넣으므로 idempotent하고, 이름 변경·삭제된 공휴일이
     * 별도 분기 없이 해결된다.
     * <p>
     * 적용 순서가 곧 정확성이다. 삭제가 삽입보다 먼저 DB에 도달하지 않으면 같은 (날짜, 출처) 키에서
     * UNIQUE 제약을 위반한다:
     * <ol>
     *     <li>해당 연도의 source=API 행 전체 삭제</li>
     *     <li>API 응답에 있는 날짜의 (MANUAL, is_holiday=true) 행 삭제 — 흡수는 삭제다</li>
     *     <li>응답 전체를 source=API로 삽입</li>
     *     <li>(MANUAL, is_holiday=false)와 COMPANY 행은 손대지 않는다 — API 행과 공존하고
     *         날짜별 최종 판정은 {@link HolidayJudgment}가 해소한다</li>
     *     <li>해당 연도의 월 마커 12개 기록</li>
     * </ol>
     * 응답이 비어 있는 해는 여기까지 오지 않는다 — 한국에 공휴일 0개인 해는 없으므로
     * 연간 0건은 적재할 데이터가 아니라 실패 신호이고, 호출자가 걸러낸다.
     */
    @Transactional
    public void applyApiSync(Year year, List<HolidayApiItem> apiItems) {
        Map<LocalDate, String> apiNameByDate = nameByDate(year, apiItems);

        holidayRepository.deleteApiRowsBetween(year.atDay(1), year.atMonth(MONTHS_PER_YEAR).atEndOfMonth());
        if (!apiNameByDate.isEmpty()) {
            holidayRepository.deleteManualHolidaysOn(apiNameByDate.keySet());
        }

        holidayRepository.saveAll(apiNameByDate.entrySet().stream()
                .map(entry -> Holiday.fromApi(entry.getKey(), entry.getValue()))
                .toList());

        holidayMonthMarkerRepository.saveAll(monthMarkersOf(year));
    }

    /**
     * 응답 항목을 날짜별로 정리한다. 같은 날짜가 두 번 오면 첫 항목을 남긴다 — 복합키가
     * (날짜, 출처)라 중복은 곧 UNIQUE 위반이므로, 트랜잭션 경계에서 한 번 더 막는다.
     * <p>
     * 요청한 해 밖의 날짜는 받지 않는다. 범위 교체는 "이 연도의 API 행"만 지우므로, 다른 해의 날짜가
     * 섞여 들어오면 그 해의 낡은 행 옆에 조용히 얹혀 원장이 어긋난다. 응답 검증을 통과한 뒤라
     * 여기 걸리면 사용자 입력이 아니라 버그다.
     */
    private Map<LocalDate, String> nameByDate(Year year, List<HolidayApiItem> apiItems) {
        Map<LocalDate, String> nameByDate = new LinkedHashMap<>();
        for (HolidayApiItem item : apiItems) {
            if (!Year.from(item.date()).equals(year)) {
                throw new IllegalArgumentException(
                        "요청한 연도 밖의 공휴일은 적재할 수 없습니다. year=" + year + ", date=" + item.date());
            }
            nameByDate.putIfAbsent(item.date(), item.name());
        }
        return nameByDate;
    }

    /**
     * 연간 동기화 한 번이 월 마커 12개를 세운다. 연 단위 마커를 따로 두지 않는 이유는
     * 계산 경로가 월 단위로 완전성을 묻기 때문이고, "7월만 재적재" 같은 부분 상태도 표현할 수 있다.
     */
    private List<HolidayMonthMarker> monthMarkersOf(Year year) {
        return IntStream.rangeClosed(1, MONTHS_PER_YEAR)
                .mapToObj(month -> HolidayMonthMarker.of(year.atMonth(month), clock.instant()))
                .toList();
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
