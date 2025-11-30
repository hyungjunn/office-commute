package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.Holiday;
import com.company.officecommute.domain.overtime.HolidayResponse;
import com.company.officecommute.repository.overtime.HolidayRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorFallbackTest {

    @Autowired private ApiConvertor apiConvertor;
    @Autowired private HolidayRepository holidayRepository;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private ApiProperties apiProperties;

    @Test
    @DisplayName("API 호출 성공 시 공휴일을 DB에 저장하고 근무일수를 계산한다")
    void countStandardWorkingDays_savesToDatabase_whenApiSucceeds() {
        YearMonth yearMonth = YearMonth.of(2025, 11);
        mockSuccessfulApiResponse(yearMonth);

        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        assertThat(workingDays).isGreaterThan(0);

        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 11);
        assertThat(savedHolidays).hasSize(2);
        assertThat(savedHolidays).containsExactlyInAnyOrder(
                LocalDate.of(2025, 11, 3),
                LocalDate.of(2025, 11, 15)
        );
    }

    @Test
    @DisplayName("API 호출 실패 시 DB에서 공휴일을 조회하여 근무일수를 계산한다 (Fallback)")
    void countStandardWorkingDays_usesDatabase_whenApiFails() {
        YearMonth yearMonth = YearMonth.of(2025, 12);

        Holiday holiday1 = new Holiday(2025, 12, LocalDate.of(2025, 12, 25));
        Holiday holiday2 = new Holiday(2025, 12, LocalDate.of(2025, 12, 31));
        holidayRepository.saveAll(List.of(holiday1, holiday2));

        // API 호출 실패 시뮬레이션 (403 Forbidden)
        mockFailedApiResponse();

        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        // 2025년 12월: 31일
        // 주말: 8일 (토요일 4개 + 일요일 4개)
        // 평일: 23일
        // 공휴일 중 평일: 2일 (25일 목요일, 31일 수요일)
        // 근무일: 23 - 2 = 21일
        assertThat(workingDays).isEqualTo(21L);
    }

    @Test
    @DisplayName("API 호출 실패하고 DB에도 데이터가 없으면 공휴일 0개로 계산한다")
    void countStandardWorkingDays_usesEmptyHolidays_whenApiFailsAndNoDatabaseData() {
        YearMonth yearMonth = YearMonth.of(2026, 1);

        // API 호출 실패 시뮬레이션
        mockFailedApiResponse();

        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        // 2026년 1월: 31일
        // 주말: 9일 (토 5개 + 일 4개)
        // 평일: 22일
        // 공휴일: 0일 (DB에 없음)
        // 근무일: 22일
        assertThat(workingDays).isEqualTo(22L);
    }

    @Test
    @DisplayName("API 호출 성공 시 기존 DB 데이터를 삭제하고 새로운 데이터로 갱신한다")
    void countStandardWorkingDays_updatesDatabase_whenApiSucceeds() {
        YearMonth yearMonth = YearMonth.of(2025, 5);

        // 기존 DB에 오래된 데이터가 있다고 가정
        Holiday oldHoliday1 = new Holiday(2025, 5, LocalDate.of(2025, 5, 1));
        Holiday oldHoliday2 = new Holiday(2025, 5, LocalDate.of(2025, 5, 15));
        holidayRepository.saveAll(List.of(oldHoliday1, oldHoliday2));

        // API 호출 성공 시뮬레이션 (새로운 데이터)
        mockSuccessfulApiResponseForMay();

        apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 5);
        assertThat(savedHolidays).hasSize(2);
        assertThat(savedHolidays).containsExactlyInAnyOrder(
                LocalDate.of(2025, 5, 5),
                LocalDate.of(2025, 5, 6)
        );
        // 기존 오래된 데이터는 삭제되었음
        assertThat(savedHolidays).doesNotContain(LocalDate.of(2025, 5, 1));
        assertThat(savedHolidays).doesNotContain(LocalDate.of(2025, 5, 15));
    }

    private void mockSuccessfulApiResponse(YearMonth yearMonth) {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse response = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item item1 = new HolidayResponse.Item();
        item1.setLocDate("20251103");

        HolidayResponse.Item item2 = new HolidayResponse.Item();
        item2.setLocDate("20251115");

        body.setItems(List.of(item1, item2));
        response.setBody(body);

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    private void mockSuccessfulApiResponseForMay() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse response = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item item1 = new HolidayResponse.Item();
        item1.setLocDate("20250505");

        HolidayResponse.Item item2 = new HolidayResponse.Item();
        item2.setLocDate("20250506");

        body.setItems(List.of(item1, item2));
        response.setBody(body);

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    private void mockFailedApiResponse() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenThrow(HttpClientErrorException.Forbidden.create(
                        "Forbidden",
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Forbidden",
                        null,
                        null,
                        null
                ));
    }
}
