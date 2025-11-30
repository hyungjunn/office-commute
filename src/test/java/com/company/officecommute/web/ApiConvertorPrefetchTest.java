package com.company.officecommute.web;

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
class ApiConvertorPrefetchTest {

    @Autowired private ApiConvertor apiConvertor;
    @Autowired private HolidayRepository holidayRepository;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private ApiProperties apiProperties;

    @Test
    @DisplayName("다음 달 공휴일 선제적 저장 성공 시 DB에 저장된다")
    void prefetchNextMonthHolidays_savesToDatabase_whenApiSucceeds() {
        // given
        YearMonth currentMonth = YearMonth.of(2025, 5);
        YearMonth nextMonth = YearMonth.of(2025, 6);

        // 다음 달(6월) API 응답 모킹
        mockSuccessfulApiResponseForJune();

        // when
        apiConvertor.prefetchNextMonthHolidays(currentMonth);

        // then
        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 6);
        assertThat(savedHolidays).hasSize(1);
        assertThat(savedHolidays).containsExactly(LocalDate.of(2025, 6, 6));
    }

    @Test
    @DisplayName("다음 달 공휴일 선제적 저장 실패 시 예외를 던지지 않는다")
    void prefetchNextMonthHolidays_doesNotThrowException_whenApiFails() {
        // given
        YearMonth currentMonth = YearMonth.of(2025, 5);

        mockFailedApiResponse();

        // when & then
        // 예외가 발생하지 않아야 함
        apiConvertor.prefetchNextMonthHolidays(currentMonth);

        // DB에 저장되지 않았는지 확인
        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 6);
        assertThat(savedHolidays).isEmpty();
    }

    @Test
    @DisplayName("선제적 저장으로 다음 달 API 실패 시에도 근무일수 계산 가능")
    void countStandardWorkingDays_usesPreFetchedData_whenCurrentMonthApiFails() {
        // given
        YearMonth mayMonth = YearMonth.of(2025, 5);
        YearMonth juneMonth = YearMonth.of(2025, 6);

        // 1단계: 5월에 6월 공휴일 미리 저장 (선제적 캐싱)
        mockSuccessfulApiResponseForJune();
        apiConvertor.prefetchNextMonthHolidays(mayMonth);

        // 2단계: 6월에 API 실패 시뮬레이션
        mockFailedApiResponse();

        // when: 6월에 근무일수 계산 (API는 실패하지만 DB에 데이터가 있음)
        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(juneMonth);

        // then
        // 2025년 6월: 30일
        // 주말: 9일 (토 4개 + 일 5개)
        // 평일: 21일
        // 공휴일 중 평일: 1일 (6일 금요일)
        // 근무일: 21 - 1 = 20일
        assertThat(workingDays).isEqualTo(20L);
    }

    private void mockSuccessfulApiResponseForJune() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse response = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item item1 = new HolidayResponse.Item();
        item1.setLocDate("20250606"); // 현충일

        body.setItems(List.of(item1));
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
