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
import java.time.Year;

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
    @DisplayName("검증을 통과한 응답은 항목 그대로 반환한다")
    void fetchHolidays_returnsItems_whenApiSucceeds() {
        mockApiResponse(normalResponse(holiday("20251103"), holiday("20251115")));

        assertThat(apiConvertor.fetchHolidays(Year.of(2025))).hasSize(2);
    }

    @Test
    @DisplayName("API 호출 실패 시 적재를 중단한다")
    void fetchHolidays_throwsException_whenApiFails() {
        mockFailedApiResponse();

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다");
    }

    @Test
    @DisplayName("HTTP 200이지만 resultCode가 오류면 공휴일 0개로 계산하지 않고 중단한다")
    void fetchHolidays_throwsException_whenResultCodeIsNotNormal() {
        HolidayResponse response = normalResponse();
        response.setHeader(header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"));
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("header가 없는 응답은 신뢰하지 않는다")
    void fetchHolidays_throwsException_whenHeaderIsMissing() {
        HolidayResponse response = normalResponse();
        response.setHeader(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("resultCode가 없습니다");
    }

    @Test
    @DisplayName("body가 없는 응답은 신뢰하지 않는다")
    void fetchHolidays_throwsException_whenBodyIsMissing() {
        HolidayResponse response = normalResponse();
        response.setBody(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("body가 없습니다");
    }

    @Test
    @DisplayName("totalCount보다 적게 수신한 잘린 응답으로는 계산하지 않는다")
    void fetchHolidays_throwsException_whenResponseIsTruncated() {
        HolidayResponse response = normalResponse(holiday("20251225"));
        response.getBody().setTotalCount(3);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("건수가 일치하지 않습니다");
    }

    @Test
    @DisplayName("totalCount가 없으면 응답의 완전성을 확인할 수 없어 계산하지 않는다")
    void fetchHolidays_throwsException_whenTotalCountIsMissing() {
        HolidayResponse response = normalResponse();
        response.getBody().setItems(null);
        response.getBody().setTotalCount(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("totalCount=null");
    }

    /**
     * 월 단위로는 0건인 달이 정상이지만(4월·11월), 공휴일이 하나도 없는 해는 한국에 없다.
     * 연간 0건은 "공휴일 없음"이 아니라 "아직 발표되지 않음"이므로 적재하지 않는다.
     */
    @Test
    @DisplayName("연간 0건 응답은 정상이 아니라 미발표 신호로 다뤄 적재를 중단한다")
    void fetchHolidays_throwsException_whenYearHasNoHoliday() {
        HolidayResponse response = normalResponse();
        response.getBody().setItems(null);
        response.getBody().setTotalCount(0);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2099)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("0건을 반환했습니다");
    }

    @Test
    @DisplayName("totalCount보다 많이 수신한 응답도 신뢰하지 않는다")
    void fetchHolidays_throwsException_whenReceivedMoreThanTotalCount() {
        HolidayResponse response = normalResponse(holiday("20251225"));
        response.getBody().setTotalCount(0);
        mockApiResponse(response);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("건수가 일치하지 않습니다");
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 계산을 중단한다")
    void fetchHolidays_throwsException_whenResponseIsNull() {
        mockApiResponse(null);

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("비어 있습니다");
    }

    @Test
    @DisplayName("dateName이 없는 항목은 원장에 적재할 수 없으므로 실패시킨다")
    void fetchHolidays_throwsException_whenDateNameIsMissing() {
        mockApiResponse(normalResponse(holiday("20251225", null)));

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("dateName이 없습니다");
    }

    @Test
    @DisplayName("요청한 연도 밖의 날짜가 섞인 응답은 신뢰하지 않는다")
    void fetchHolidays_throwsException_whenDateIsOutsideRequestedYear() {
        mockApiResponse(normalResponse(holiday("20251225")));

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2026)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("요청한 연도 밖의 날짜");
    }

    @Test
    @DisplayName("locdate를 해석할 수 없는 항목은 실패시킨다")
    void fetchHolidays_throwsException_whenLocdateIsUnparsable() {
        mockApiResponse(normalResponse(holiday("2025-12-25")));

        assertThatThrownBy(() -> apiConvertor.fetchHolidays(Year.of(2025)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("locdate를 해석할 수 없습니다");
    }

    private void mockApiResponse(HolidayResponse response) {
        when(apiProperties.combineURL(any()))
                .thenReturn("http://fake-api.com");
        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    private void mockFailedApiResponse() {
        when(apiProperties.combineURL(any()))
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
