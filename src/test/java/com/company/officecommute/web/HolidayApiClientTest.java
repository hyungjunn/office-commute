package com.company.officecommute.web;

import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayApiClientTest {

    // 포털이 발급하는 URL 인코딩 형태의 키(디코딩하면 "service+key").
    private static final String ENCODED_SERVICE_KEY = "service%2Bkey";

    @Mock
    private RestTemplate restTemplate;

    private HolidayApiClient holidayApiClient;

    @BeforeEach
    void setUp() {
        HolidayApiProperties properties = new HolidayApiProperties();
        properties.setUrl("https://fake-api.com/getRestDeInfo");
        properties.setServiceKey(ENCODED_SERVICE_KEY);
        holidayApiClient = new HolidayApiClient(restTemplate, properties);
    }

    @Test
    @DisplayName("응답의 locdate를 날짜 집합으로 변환한다")
    void getHolidays_returnsParsedDates_whenApiSucceeds() {
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20240505"),
                HolidayResponseFixture.holiday("20240506"),
                HolidayResponseFixture.holiday("20240515")
        ));

        Set<LocalDate> holidays = holidayApiClient.getHolidays(YearMonth.of(2024, 5));

        assertThat(holidays).containsExactlyInAnyOrder(
                LocalDate.of(2024, 5, 5),
                LocalDate.of(2024, 5, 6),
                LocalDate.of(2024, 5, 15)
        );
    }

    @Test
    @DisplayName("공휴일 0건 응답의 계약은 items 없음 + totalCount=0이다")
    void getHolidays_returnsEmptySet_whenTotalCountIsZero() {
        HolidayResponse response = HolidayResponseFixture.normalResponse();
        response.getBody().setItems(null);
        response.getBody().setTotalCount(0);
        mockApiResponse(response);

        Set<LocalDate> holidays = holidayApiClient.getHolidays(YearMonth.of(2024, 6));

        assertThat(holidays).isEmpty();
    }

    @Test
    @DisplayName("인코딩된 serviceKey는 재인코딩 없이 그대로 요청 URI에 실린다")
    void getHolidays_buildsUriWithoutReencodingServiceKey() {
        mockApiResponse(HolidayResponseFixture.normalResponse());

        holidayApiClient.getHolidays(YearMonth.of(2024, 5));

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(HolidayResponse.class));
        // %2B가 %252B로 이중 인코딩되면 서버가 키를 알아보지 못해 인증에 실패한다.
        assertThat(uriCaptor.getValue().getRawQuery())
                .isEqualTo("serviceKey=" + ENCODED_SERVICE_KEY + "&solYear=2024&solMonth=05&numOfRows=100");
    }

    @Test
    @DisplayName("API 호출 실패 시 계산에 쓸 수 없음을 알린다")
    void getHolidays_throwsException_whenApiFails() {
        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenThrow(HttpClientErrorException.Forbidden.create(
                        "Forbidden", HttpStatus.FORBIDDEN, "Forbidden", null, null, null));

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다");
    }

    @Test
    @DisplayName("resultCode가 오류면 공휴일 0개로 해석하지 않고 중단한다")
    void getHolidays_throwsException_whenResultCodeIsNotNormal() {
        HolidayResponse response = HolidayResponseFixture.normalResponse();
        response.setHeader(HolidayResponseFixture.header("30", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"));
        mockApiResponse(response);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("header가 없는 응답은 신뢰하지 않는다")
    void getHolidays_throwsException_whenHeaderIsMissing() {
        HolidayResponse response = HolidayResponseFixture.normalResponse();
        response.setHeader(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("resultCode가 없습니다");
    }

    @Test
    @DisplayName("body가 없는 응답은 신뢰하지 않는다")
    void getHolidays_throwsException_whenBodyIsMissing() {
        HolidayResponse response = HolidayResponseFixture.normalResponse();
        response.setBody(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("body가 없습니다");
    }

    @Test
    @DisplayName("totalCount보다 적게 수신한 잘린 응답으로는 계산하지 않는다")
    void getHolidays_throwsException_whenResponseIsTruncated() {
        HolidayResponse response = HolidayResponseFixture.normalResponse(HolidayResponseFixture.holiday("20251225"));
        response.getBody().setTotalCount(3);
        mockApiResponse(response);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("건수가 일치하지 않습니다");
    }

    @Test
    @DisplayName("totalCount가 없으면 응답의 완전성을 확인할 수 없어 계산하지 않는다")
    void getHolidays_throwsException_whenTotalCountIsMissing() {
        HolidayResponse response = HolidayResponseFixture.normalResponse();
        response.getBody().setItems(null);
        response.getBody().setTotalCount(null);
        mockApiResponse(response);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("totalCount=null");
    }

    @Test
    @DisplayName("totalCount보다 많이 수신한 응답도 신뢰하지 않는다")
    void getHolidays_throwsException_whenReceivedMoreThanTotalCount() {
        HolidayResponse response = HolidayResponseFixture.normalResponse(HolidayResponseFixture.holiday("20251225"));
        response.getBody().setTotalCount(0);
        mockApiResponse(response);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("건수가 일치하지 않습니다");
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 계산을 중단한다")
    void getHolidays_throwsException_whenResponseIsNull() {
        mockApiResponse(null);

        assertThatThrownBy(() -> holidayApiClient.getHolidays(YearMonth.of(2025, 12)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("비어 있습니다");
    }

    private void mockApiResponse(HolidayResponse response) {
        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    static class HolidayResponseFixture {

        static HolidayResponse normalResponse(HolidayResponse.Item... items) {
            HolidayResponse response = new HolidayResponse();
            response.setHeader(header("00", "NORMAL SERVICE."));

            HolidayResponse.Body body = new HolidayResponse.Body();
            body.setItems(List.of(items));
            body.setTotalCount(items.length);
            response.setBody(body);
            return response;
        }

        static HolidayResponse.Header header(String resultCode, String resultMsg) {
            HolidayResponse.Header header = new HolidayResponse.Header();
            header.setResultCode(resultCode);
            header.setResultMsg(resultMsg);
            return header;
        }

        static HolidayResponse.Item holiday(String locdate) {
            HolidayResponse.Item item = new HolidayResponse.Item();
            item.setLocdate(locdate);
            item.setIsHoliday("Y");
            return item;
        }
    }
}
