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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayLedgerServiceTest {

    private static final Year YEAR = Year.of(2026);
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
    @DisplayName("해당 연도의 API 행을 통째로 지운 뒤 응답 전체를 다시 넣는다")
    void replacesApiRowsForWholeYear() {
        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        verify(holidayRepository).deleteApiRowsBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        ArgumentCaptor<List<Holiday>> saved = ArgumentCaptor.captor();
        verify(holidayRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(Holiday::getHolidayDate, Holiday::getName, Holiday::getSource)
                .containsExactly(tuple(CONSTITUTION_DAY, "제헌절", HolidaySource.API));
    }

    /**
     * Hibernate는 flush에서 INSERT를 DELETE보다 먼저 실행한다. 삭제가 먼저 나가지 않으면
     * 같은 (날짜, 출처) 키의 재삽입이 UNIQUE 제약을 위반하므로 이 순서 자체가 계약이다.
     */
    @Test
    @DisplayName("삭제가 삽입보다 먼저다 — 순서가 어긋나면 UNIQUE 제약을 위반한다")
    void deletesBeforeInserting() {
        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        InOrder inOrder = inOrder(holidayRepository);
        inOrder.verify(holidayRepository).deleteApiRowsBetween(any(), any());
        inOrder.verify(holidayRepository).deleteManualHolidaysOn(anyCollection());
        inOrder.verify(holidayRepository).saveAll(any());
    }

    @Test
    @DisplayName("감사 로그용 조회는 삭제보다 먼저다 — 지운 뒤에 읽으면 무엇이 바뀌었는지 알 수 없다")
    void readsExistingRowsBeforeDeleting() {
        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        InOrder inOrder = inOrder(holidayRepository);
        inOrder.verify(holidayRepository).findByHolidayDateBetweenOrderByHolidayDate(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        inOrder.verify(holidayRepository).deleteApiRowsBetween(any(), any());
    }

    @Test
    @DisplayName("API가 공휴일로 준 날짜의 수동 등록 휴일은 흡수해 삭제한다 — API 행이 대신한다")
    void absorbsManualHolidayOnApiDate() {
        holidayLedgerService.applyApiSync(YEAR, List.of(
                new HolidayApiItem(CONSTITUTION_DAY, "제헌절"),
                new HolidayApiItem(LocalDate.of(2026, 1, 1), "1월1일")
        ));

        ArgumentCaptor<Set<LocalDate>> absorbedDates = ArgumentCaptor.captor();
        verify(holidayRepository).deleteManualHolidaysOn(absorbedDates.capture());
        assertThat(absorbedDates.getValue())
                .containsExactlyInAnyOrder(CONSTITUTION_DAY, LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("응답이 비어 있으면 흡수할 날짜가 없으므로 수동 행 삭제를 시도하지 않는다")
    void skipsAbsorptionWhenResponseIsEmpty() {
        holidayLedgerService.applyApiSync(YEAR, List.of());

        verify(holidayRepository).deleteApiRowsBetween(any(), any());
        verify(holidayRepository, never()).deleteManualHolidaysOn(anyCollection());
    }

    @Test
    @DisplayName("같은 날짜가 두 번 오면 첫 항목만 남긴다 — 복합키 중복은 곧 UNIQUE 위반이다")
    void keepsFirstItemOnDuplicateDate() {
        holidayLedgerService.applyApiSync(YEAR, List.of(
                new HolidayApiItem(CONSTITUTION_DAY, "제헌절"),
                new HolidayApiItem(CONSTITUTION_DAY, "제헌절(중복)")
        ));

        ArgumentCaptor<List<Holiday>> saved = ArgumentCaptor.captor();
        verify(holidayRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .extracting(Holiday::getName).isEqualTo("제헌절");
    }

    @Test
    @DisplayName("요청한 해 밖의 날짜가 섞이면 원장을 건드리지 않고 실패한다")
    void rejectsDateOutsideRequestedYear() {
        assertThatThrownBy(() -> holidayLedgerService.applyApiSync(
                YEAR, List.of(new HolidayApiItem(LocalDate.of(2027, 1, 1), "1월1일"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("요청한 연도 밖");

        verify(holidayRepository, never()).deleteApiRowsBetween(any(), any());
        verify(holidayRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("연간 동기화 한 번이 월 마커 12개를 세운다")
    void writesTwelveMonthMarkers() {
        holidayLedgerService.applyApiSync(YEAR, List.of(new HolidayApiItem(CONSTITUTION_DAY, "제헌절")));

        ArgumentCaptor<List<HolidayMonthMarker>> savedMarkers = ArgumentCaptor.captor();
        verify(holidayMonthMarkerRepository).saveAll(savedMarkers.capture());
        assertThat(savedMarkers.getValue())
                .hasSize(12)
                .allSatisfy(marker -> assertThat(marker.getSyncedAt()).isEqualTo(SYNC_INSTANT))
                .extracting(HolidayMonthMarker::getMonth)
                .containsExactly(
                        YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3),
                        YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6),
                        YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9),
                        YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12));
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
