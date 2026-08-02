package com.company.officecommute.service.holiday;

import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.web.ApiConvertor;
import com.company.officecommute.web.HolidayApiItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidaySyncServiceTest {

    private static final Year YEAR = Year.of(2026);
    private static final LocalDate CONSTITUTION_DAY = LocalDate.of(2026, 7, 17);

    @Mock
    private ApiConvertor apiConvertor;
    @Mock
    private HolidayLedgerService holidayLedgerService;

    private HolidaySyncService holidaySyncService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-02T00:30:00Z"), ZoneId.of("Asia/Seoul"));
        holidaySyncService = new HolidaySyncService(apiConvertor, holidayLedgerService, fixedClock);
    }

    @Test
    @DisplayName("한 해를 한 번 조회해 그대로 원장에 반영하고 적재 건수를 돌려준다")
    void syncsWholeYearIntoLedger() {
        List<HolidayApiItem> apiItems = List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절"));
        when(apiConvertor.fetchHolidays(YEAR)).thenReturn(apiItems);

        assertThat(holidaySyncService.syncYear(YEAR)).isEqualTo(1);

        verify(holidayLedgerService).applyApiSync(YEAR, apiItems);
    }

    @Test
    @DisplayName("정기 동기화는 올해부터 +2년까지 돈다")
    void syncsCurrentYearAndTwoAhead() {
        givenHolidaysFor(Year.of(2026), Year.of(2027), Year.of(2028));

        assertThat(holidaySyncService.syncUpcomingYears())
                .containsExactly(Year.of(2026), Year.of(2027), Year.of(2028));
    }

    /**
     * +2년차는 아직 공휴일이 발표되지 않아 0건이 오는 것이 정상적인 실패다. 그 해를 건너뛰는 것이
     * 곧 "원장이 커버하는 범위"의 표현이고, 계산 경로는 마커 없는 달을 거부해 스스로를 지킨다.
     */
    @Test
    @DisplayName("한 해가 실패해도 나머지 해의 적재는 진행한다")
    void continuesWhenOneYearFails() {
        givenHolidaysFor(Year.of(2026), Year.of(2027));
        when(apiConvertor.fetchHolidays(Year.of(2028)))
                .thenThrow(new HolidayDataUnavailableException("공휴일 API가 해당 연도에 0건을 반환했습니다."));

        assertThat(holidaySyncService.syncUpcomingYears())
                .containsExactly(Year.of(2026), Year.of(2027));

        verify(holidayLedgerService, never()).applyApiSync(eq(Year.of(2028)), anyList());
    }

    @Test
    @DisplayName("모든 해가 실패하면 원장을 전혀 건드리지 않는다")
    void touchesNothingWhenEveryYearFails() {
        when(apiConvertor.fetchHolidays(any()))
                .thenThrow(new HolidayDataUnavailableException("공휴일 API 호출 실패"));

        assertThat(holidaySyncService.syncUpcomingYears()).isEmpty();

        verify(holidayLedgerService, never()).applyApiSync(any(), anyList());
    }

    private void givenHolidaysFor(Year... years) {
        for (Year year : years) {
            when(apiConvertor.fetchHolidays(year))
                    .thenReturn(List.of(new HolidayApiItem(year.atDay(1), "1월1일")));
        }
    }

    @Test
    @DisplayName("API 호출이 실패하면 원장과 마커를 건드리지 않는다")
    void leavesLedgerUntouchedWhenFetchFails() {
        when(apiConvertor.fetchHolidays(YEAR))
                .thenThrow(new HolidayDataUnavailableException("공휴일 API가 오류를 반환했습니다."));

        assertThatThrownBy(() -> holidaySyncService.syncYear(YEAR))
                .isInstanceOf(HolidayDataUnavailableException.class);

        verify(holidayLedgerService, never()).applyApiSync(any(), anyList());
    }
}
