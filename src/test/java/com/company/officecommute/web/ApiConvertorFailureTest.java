package com.company.officecommute.web;

import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.YearMonth;

import static com.company.officecommute.web.ApiConvertorTest.HolidayResponseFixture.header;
import static com.company.officecommute.web.ApiConvertorTest.HolidayResponseFixture.holiday;
import static com.company.officecommute.web.ApiConvertorTest.HolidayResponseFixture.normalResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorFailureTest {

    @Autowired private ApiConvertor apiConvertor;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private ApiProperties apiProperties;

    @Test
    @DisplayName("API 호출 성공 시 응답 데이터로 근무일수를 계산한다")
    void countStandardWorkingDays_calculatesWorkingDays_whenApiSucceeds() {
        YearMonth yearMonth = YearMonth.of(2025, 11);
        mockApiResponse(normalResponse(holiday("20251103"), holiday("20251115")));

        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        assertThat(workingDays).isEqualTo(19);
    }

    @Test
    @DisplayName("API 호출 실패 시 계산을 중단한다")
    void countStandardWorkingDays_throwsException_whenApiFails() {
        YearMonth yearMonth = YearMonth.of(2025, 12);

        mockFailedApiResponse();

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(yearMonth))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다");
    }

    @Test
    @DisplayName("HTTP 200이지만 resultCode가 오류면 공휴일 0개로 계산하지 않고 중단한다")
    void countStandardWorkingDays_throwsException_whenResultCodeIsNotNormal() {
        HolidayResponse response = normalResponse();
        response.setHeader(header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"));
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("header가 없는 응답은 신뢰하지 않는다")
    void countStandardWorkingDays_throwsException_whenHeaderIsMissing() {
        HolidayResponse response = normalResponse();
        response.setHeader(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("resultCode가 없습니다");
    }

    @Test
    @DisplayName("body가 없는 응답은 신뢰하지 않는다")
    void countStandardWorkingDays_throwsException_whenBodyIsMissing() {
        HolidayResponse response = normalResponse();
        response.setBody(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("body가 없습니다");
    }

    @Test
    @DisplayName("totalCount보다 적게 수신한 잘린 응답으로는 계산하지 않는다")
    void countStandardWorkingDays_throwsException_whenResponseIsTruncated() {
        HolidayResponse response = normalResponse(holiday("20251225"));
        response.getBody().setTotalCount(3);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("건수가 일치하지 않습니다");
    }

    @Test
    @DisplayName("totalCount가 없으면 응답의 완전성을 확인할 수 없어 계산하지 않는다")
    void countStandardWorkingDays_throwsException_whenTotalCountIsMissing() {
        HolidayResponse response = normalResponse();
        response.getBody().setItems(null);
        response.getBody().setTotalCount(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("totalCount=null");
    }

    @Test
    @DisplayName("totalCount보다 많이 수신한 응답도 신뢰하지 않는다")
    void countStandardWorkingDays_throwsException_whenReceivedMoreThanTotalCount() {
        HolidayResponse response = normalResponse(holiday("20251225"));
        response.getBody().setTotalCount(0);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("건수가 일치하지 않습니다");
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 계산을 중단한다")
    void countStandardWorkingDays_throwsException_whenResponseIsNull() {
        mockApiResponse(null);

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("비어 있습니다");
    }

    @Test
    @DisplayName("dateName이 없는 항목은 원장에 적재할 수 없으므로 실패시킨다")
    void fetchHolidays_throwsException_whenDateNameIsMissing() {
        mockApiResponse(normalResponse(holiday("20251225", null)));

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("dateName이 없습니다");
    }

    @Test
    @DisplayName("요청한 월 밖의 날짜가 섞인 응답은 신뢰하지 않는다")
    void fetchHolidays_throwsException_whenDateIsOutsideRequestedMonth() {
        mockApiResponse(normalResponse(holiday("20251225")));

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(YearMonth.of(2025, 11)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("요청한 월 밖의 날짜");
    }

    @Test
    @DisplayName("locdate를 해석할 수 없는 항목은 실패시킨다")
    void fetchHolidays_throwsException_whenLocdateIsUnparsable() {
        mockApiResponse(normalResponse(holiday("2025-12-25")));

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("locdate를 해석할 수 없습니다");
    }

    private void mockApiResponse(HolidayResponse response) {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");
        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    private void mockFailedApiResponse() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenThrow(HttpClientErrorException.Forbidden.create(
                        "Forbidden",
                        HttpStatus.FORBIDDEN,
                        "Forbidden",
                        null,
                        null,
                        null
                ));
    }
}
