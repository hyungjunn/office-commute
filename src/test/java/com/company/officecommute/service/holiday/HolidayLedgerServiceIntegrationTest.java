package com.company.officecommute.service.holiday;

import com.company.officecommute.domain.holiday.Holiday;
import com.company.officecommute.domain.holiday.HolidaySource;
import com.company.officecommute.repository.holiday.HolidayMonthMarkerRepository;
import com.company.officecommute.repository.holiday.HolidayRepository;
import com.company.officecommute.web.HolidayApiItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * 목으로는 증명되지 않는 부분을 확인한다: 같은 (날짜, 출처) 키를 지우고 다시 넣는 흐름이
 * 실제로 UNIQUE 제약을 넘어가는지, 그리고 흡수·보존이 커밋 후에도 그대로인지.
 * <p>
 * 트랜잭션을 테스트에 걸지 않는다 — 서비스가 자기 트랜잭션에서 커밋하는 것이 검증 대상이다.
 */
@SpringBootTest
class HolidayLedgerServiceIntegrationTest {

    private static final Year YEAR = Year.of(2026);
    private static final LocalDate NEW_YEAR = LocalDate.of(2026, 1, 1);
    private static final LocalDate FOUNDATION_DAY = LocalDate.of(2026, 5, 20);
    private static final LocalDate CONSTITUTION_DAY = LocalDate.of(2026, 7, 17);

    @Autowired
    private HolidayLedgerService holidayLedgerService;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private HolidayMonthMarkerRepository holidayMonthMarkerRepository;

    @BeforeEach
    void setUp() {
        holidayRepository.deleteAll();
        holidayMonthMarkerRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 날짜의 API 행을 지우고 다시 넣어도 UNIQUE 제약을 위반하지 않는다")
    void replacesApiRowOnSameDateWithoutConstraintViolation() {
        holidayRepository.save(Holiday.fromApi(CONSTITUTION_DAY, "임시공휴일"));

        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        assertThat(holidayRepository.findAll())
                .extracting(Holiday::getHolidayDate, Holiday::getName, Holiday::getSource)
                .containsExactly(tuple(CONSTITUTION_DAY, "제헌절", HolidaySource.API));
    }

    @Test
    @DisplayName("흡수는 삭제다 — API가 준 날짜의 수동 등록 휴일은 사라지고 API 행만 남는다")
    void absorbsManualHolidayOnApiDate() {
        holidayRepository.save(Holiday.manualHoliday(NEW_YEAR, "1월1일(포털 미반영 보정)"));

        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(NEW_YEAR, "1월1일")));

        assertThat(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(NEW_YEAR, NEW_YEAR))
                .extracting(Holiday::getSource, Holiday::getName)
                .containsExactly(tuple(HolidaySource.API, "1월1일"));
    }

    @Test
    @DisplayName("부정 오버라이드와 회사 지정 휴일은 동기화가 건드리지 않는다")
    void keepsNegativeOverrideAndCompanyRows() {
        holidayRepository.saveAll(List.of(
                Holiday.manualWorkingDay(CONSTITUTION_DAY, "정상 근무(전사 공지)"),
                Holiday.companyHoliday(FOUNDATION_DAY, "창립기념일")
        ));

        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        assertThat(holidayRepository.findAll())
                .extracting(Holiday::getHolidayDate, Holiday::getSource, Holiday::isHoliday)
                .containsExactlyInAnyOrder(
                        tuple(CONSTITUTION_DAY, HolidaySource.API, true),
                        tuple(CONSTITUTION_DAY, HolidaySource.MANUAL, false),
                        tuple(FOUNDATION_DAY, HolidaySource.COMPANY, true)
                );
        // 부정 오버라이드가 API 행을 이기므로 그 날은 휴일이 아니다.
        assertThat(holidayLedgerService.getHolidayDates(YearMonth.of(2026, 7))).isEmpty();
        assertThat(holidayLedgerService.getHolidayDates(YearMonth.of(2026, 5))).containsExactly(FOUNDATION_DAY);
    }

    @Test
    @DisplayName("응답에서 사라진 API 행은 연도 전체에서 지워진다")
    void dropsApiRowsAbsentFromResponse() {
        holidayRepository.save(Holiday.fromApi(LocalDate.of(2026, 3, 1), "잘못 적재된 날"));

        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(NEW_YEAR, "1월1일")));

        assertThat(holidayRepository.findAll())
                .extracting(Holiday::getHolidayDate)
                .containsExactly(NEW_YEAR);
    }

    @Test
    @DisplayName("다른 해의 행은 범위 교체에 휩쓸리지 않는다")
    void leavesOtherYearsUntouched() {
        holidayRepository.save(Holiday.fromApi(LocalDate.of(2025, 12, 25), "기독탄신일"));

        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(NEW_YEAR, "1월1일")));

        assertThat(holidayRepository.findAll())
                .extracting(Holiday::getHolidayDate)
                .containsExactlyInAnyOrder(LocalDate.of(2025, 12, 25), NEW_YEAR);
    }

    @Test
    @DisplayName("같은 응답으로 다시 동기화해도 결과가 같다 — idempotent")
    void isIdempotent() {
        List<HolidayApiItem> apiItems = List.of(
                new HolidayApiItem(NEW_YEAR, "1월1일"),
                new HolidayApiItem(CONSTITUTION_DAY, "제헌절")
        );

        holidayLedgerService.applyApiSync(YEAR, apiItems);
        holidayLedgerService.applyApiSync(YEAR, apiItems);

        assertThat(holidayRepository.findAll())
                .extracting(Holiday::getHolidayDate)
                .containsExactlyInAnyOrder(NEW_YEAR, CONSTITUTION_DAY);
    }

    @Test
    @DisplayName("연간 동기화가 월 마커 12개를 세워 12개월 모두 계산 가능해진다")
    void writesTwelveMonthMarkers() {
        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(NEW_YEAR, "1월1일")));

        assertThat(holidayMonthMarkerRepository.count()).isEqualTo(12);
        for (int month = 1; month <= 12; month++) {
            assertThat(holidayMonthMarkerRepository.existsByMonth(YEAR.atMonth(month))).isTrue();
        }
    }
}
