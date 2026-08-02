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

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        holidaySyncService = new HolidaySyncService(apiConvertor, holidayLedgerService);
    }

    @Test
    @DisplayName("한 해를 한 번 조회해 그대로 원장에 반영한다")
    void syncsWholeYearIntoLedger() {
        List<HolidayApiItem> apiItems = List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절"));
        when(apiConvertor.fetchHolidays(YEAR)).thenReturn(apiItems);

        holidaySyncService.syncYear(YEAR);

        verify(holidayLedgerService).applyApiSync(YEAR, apiItems);
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
