package com.company.officecommute.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorTest {

    @Autowired
    private ApiConvertor apiConvertor;

    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean
    private ApiProperties apiProperties;

    @Test
    @DisplayName("한 해 전체를 검증된 날짜·이름으로 반환한다 — 원장 동기화의 입력")
    void fetchHolidays_returnsWholeYearWithNames() {
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20260101", "1월1일"),
                HolidayResponseFixture.holiday("20260717", "제헌절")
        ));

        List<HolidayApiItem> items = apiConvertor.fetchHolidays(Year.of(2026));

        assertThat(items).containsExactly(
                new HolidayApiItem(LocalDate.of(2026, 1, 1), "1월1일"),
                new HolidayApiItem(LocalDate.of(2026, 7, 17), "제헌절"));
    }

    @Test
    @DisplayName("연 단위 조회이므로 URL에 월을 싣지 않는다")
    void fetchHolidays_requestsWholeYear() {
        mockApiResponse(HolidayResponseFixture.normalResponse(HolidayResponseFixture.holiday("20260101")));

        apiConvertor.fetchHolidays(Year.of(2026));

        verify(apiProperties).combineURL("2026");
    }

    /**
     * 연도별 공휴일 지정 여부는 API의 책임이다. 제헌절은 2026년 재지정 이후에만 응답에 나타난다 —
     * 날짜도 플래그도 코드에 박지 않고 응답을 그대로 받아들인다.
     */
    @Test
    @DisplayName("응답 항목은 정의상 모두 공휴일이므로 그대로 옮긴다")
    void fetchHolidays_trustsEveryReturnedItem() {
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20260717", "  제헌절  ")
        ));

        assertThat(apiConvertor.fetchHolidays(Year.of(2026)))
                .containsExactly(new HolidayApiItem(LocalDate.of(2026, 7, 17), "제헌절"));
    }

    private void mockApiResponse(HolidayResponse response) {
        when(apiProperties.combineURL(any()))
                .thenReturn("http://fake-api.com");
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
            return holiday(locdate, "공휴일");
        }

        static HolidayResponse.Item holiday(String locdate, String dateName) {
            HolidayResponse.Item item = new HolidayResponse.Item();
            item.setLocdate(locdate);
            item.setDateName(dateName);
            item.setIsHoliday("Y");
            return item;
        }
    }
}
