package com.company.officecommute.service.holiday;

import com.company.officecommute.domain.holiday.Holiday;
import com.company.officecommute.domain.holiday.HolidayMonthMarker;
import com.company.officecommute.domain.holiday.HolidayMonthNotLoadedException;
import com.company.officecommute.domain.holiday.HolidaySource;
import com.company.officecommute.repository.holiday.HolidayMonthMarkerRepository;
import com.company.officecommute.repository.holiday.HolidayRepository;
import com.company.officecommute.web.HolidayApiItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayLedgerServiceTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final LocalDate CONSTITUTION_DAY = LocalDate.of(2026, 7, 17);
    private static final Instant SYNC_INSTANT = Instant.parse("2026-08-01T00:30:00Z");

    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private HolidayMonthMarkerRepository holidayMonthMarkerRepository;

    private HolidayLedgerService holidayLedgerService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(SYNC_INSTANT, ZoneId.of("Asia/Seoul"));
        holidayLedgerService = new HolidayLedgerService(
                holidayRepository, holidayMonthMarkerRepository, fixedClock);
    }

    @Test
    @DisplayName("원장에 없는 날짜는 API 출처로 새로 저장하고 마커를 남긴다")
    void savesNewHolidaysAndMarker() {
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of());

        holidayLedgerService.applyApiSync(JULY, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        ArgumentCaptor<Holiday> savedHoliday = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(savedHoliday.capture());
        assertThat(savedHoliday.getValue().getHolidayDate()).isEqualTo(CONSTITUTION_DAY);
        assertThat(savedHoliday.getValue().getName()).isEqualTo("제헌절");
        assertThat(savedHoliday.getValue().getSource()).isEqualTo(HolidaySource.API);

        ArgumentCaptor<HolidayMonthMarker> savedMarker = ArgumentCaptor.forClass(HolidayMonthMarker.class);
        verify(holidayMonthMarkerRepository).save(savedMarker.capture());
        assertThat(savedMarker.getValue().getMonth()).isEqualTo(JULY);
        assertThat(savedMarker.getValue().getSyncedAt()).isEqualTo(SYNC_INSTANT);
    }

    @Test
    @DisplayName("MANUAL 행은 API가 같은 날짜를 줘도 갱신하지 않고, API 행은 그 옆에 따로 적재된다")
    void manualRowSurvivesAlongsideApiRowOnSameDate() {
        Holiday manualRow = Holiday.manualWorkingDay(CONSTITUTION_DAY, "정상 근무(API 오적재 보정)");
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of(manualRow));

        holidayLedgerService.applyApiSync(JULY, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        verify(holidayRepository, never()).delete(any());
        assertThat(manualRow.getName()).isEqualTo("정상 근무(API 오적재 보정)");
        assertThat(manualRow.isHoliday()).isFalse();

        ArgumentCaptor<Holiday> savedHoliday = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository).save(savedHoliday.capture());
        assertThat(savedHoliday.getValue().getSource()).isEqualTo(HolidaySource.API);
        assertThat(savedHoliday.getValue().getHolidayDate()).isEqualTo(CONSTITUTION_DAY);
    }

    @Test
    @DisplayName("MANUAL 행은 API 응답에 없어도 삭제하지 않는다")
    void manualRowSurvivesWhenAbsentFromApi() {
        Holiday manualRow = Holiday.manualHoliday(LocalDate.of(2026, 6, 3), "제21대 대통령 선거(사후 지정)");
        YearMonth june = YearMonth.of(2026, 6);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(june.atDay(1), june.atEndOfMonth()))
                .thenReturn(List.of(manualRow));

        holidayLedgerService.applyApiSync(june, List.of());

        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("COMPANY 행도 동기화가 삭제·갱신하지 않는다 — API가 줄 리 없는 회사 지정 휴일이다")
    void companyRowSurvivesSync() {
        Holiday companyRow = Holiday.companyHoliday(LocalDate.of(2026, 7, 20), "창립기념일");
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of(companyRow));

        holidayLedgerService.applyApiSync(JULY, List.of());

        verify(holidayRepository, never()).delete(any());
        verify(holidayRepository, never()).save(any());
        assertThat(companyRow.getName()).isEqualTo("창립기념일");
    }

    @Test
    @DisplayName("API 행의 이름 변경은 기존 행에 반영한다 — 새로 저장하지 않는다")
    void renamesExistingApiRow() {
        Holiday apiRow = Holiday.fromApi(CONSTITUTION_DAY, "임시공휴일");
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of(apiRow));

        holidayLedgerService.applyApiSync(JULY, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        assertThat(apiRow.getName()).isEqualTo("제헌절");
        verify(holidayRepository, never()).save(any());
        verify(holidayRepository, never()).delete(any());
    }

    @Test
    @DisplayName("API 응답에서 사라진 API 행은 삭제한다")
    void deletesApiRowAbsentFromApi() {
        Holiday staleApiRow = Holiday.fromApi(LocalDate.of(2026, 7, 20), "잘못 적재된 날");
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of(staleApiRow));

        holidayLedgerService.applyApiSync(JULY, List.of());

        verify(holidayRepository).delete(staleApiRow);
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("공휴일이 0건인 달도 마커를 남긴다 — '정상 0개'와 '미적재'를 구분하는 근거")
    void emptyMonthStillGetsMarker() {
        YearMonth april = YearMonth.of(2026, 4);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(april.atDay(1), april.atEndOfMonth()))
                .thenReturn(List.of());

        holidayLedgerService.applyApiSync(april, List.of());

        ArgumentCaptor<HolidayMonthMarker> savedMarker = ArgumentCaptor.forClass(HolidayMonthMarker.class);
        verify(holidayMonthMarkerRepository).save(savedMarker.capture());
        assertThat(savedMarker.getValue().getMonth()).isEqualTo(april);
        verify(holidayRepository, never()).save(any());
    }

    @Test
    @DisplayName("적재 마커가 없는 달은 공휴일 0개가 아니라 계산 거부다")
    void refusesUnloadedMonth() {
        when(holidayMonthMarkerRepository.existsByMonth(JULY)).thenReturn(false);

        assertThatThrownBy(() -> holidayLedgerService.getHolidayDates(JULY))
                .isInstanceOf(HolidayMonthNotLoadedException.class);
    }

    @Test
    @DisplayName("마커가 있는 달은 출처와 무관하게 휴일 날짜를 반환한다")
    void returnsHolidayDatesForLoadedMonth() {
        when(holidayMonthMarkerRepository.existsByMonth(JULY)).thenReturn(true);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of(
                        Holiday.fromApi(CONSTITUTION_DAY, "제헌절"),
                        Holiday.manualHoliday(LocalDate.of(2026, 7, 27), "임시공휴일(사후 지정)"),
                        Holiday.companyHoliday(LocalDate.of(2026, 7, 30), "창립기념일")
                ));

        Set<LocalDate> holidayDates = holidayLedgerService.getHolidayDates(JULY);

        assertThat(holidayDates).containsExactlyInAnyOrder(
                CONSTITUTION_DAY, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 30));
    }

    @Test
    @DisplayName("부정 오버라이드가 걸린 날짜는 API 행이 있어도 휴일에서 빠진다")
    void excludesDateWithNegativeOverride() {
        when(holidayMonthMarkerRepository.existsByMonth(JULY)).thenReturn(true);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(JULY.atDay(1), JULY.atEndOfMonth()))
                .thenReturn(List.of(
                        Holiday.fromApi(CONSTITUTION_DAY, "제헌절"),
                        Holiday.manualWorkingDay(CONSTITUTION_DAY, "정상 근무(전사 공지)")
                ));

        assertThat(holidayLedgerService.getHolidayDates(JULY)).isEmpty();
    }

    @Test
    @DisplayName("마커가 있는 0건 달은 빈 결과가 정상이다")
    void loadedEmptyMonthReturnsEmptySet() {
        YearMonth april = YearMonth.of(2026, 4);
        when(holidayMonthMarkerRepository.existsByMonth(april)).thenReturn(true);
        when(holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(april.atDay(1), april.atEndOfMonth()))
                .thenReturn(List.of());

        assertThat(holidayLedgerService.getHolidayDates(april)).isEmpty();
    }
}
