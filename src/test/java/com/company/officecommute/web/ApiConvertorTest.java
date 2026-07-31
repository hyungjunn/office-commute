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
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void _2024년_5월의_기준_근로_시간을_구하는_메서드를_검증하라() {
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20240505"),
                HolidayResponseFixture.holiday("20240506"),
                HolidayResponseFixture.holiday("20240515")
        ));

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 5));

        assertThat(numberOfStandardWorkingDays).isEqualTo(21L);
    }

    @Test
    void 공휴일이_없는_달의_API_응답을_처리한다() {
        mockApiResponse(HolidayResponseFixture.normalResponse());

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 6));

        assertThat(numberOfStandardWorkingDays).isEqualTo(20L);
    }

    @Test
    @DisplayName("공휴일 0건 응답의 계약은 items 없음 + totalCount=0이다")
    void countStandardWorkingDays_treatsNullItemsAsNoHoliday_whenTotalCountIsZero() {
        HolidayResponse response = HolidayResponseFixture.normalResponse();
        response.getBody().setItems(null);
        response.getBody().setTotalCount(0);
        mockApiResponse(response);

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 6));

        assertThat(numberOfStandardWorkingDays).isEqualTo(20L);
    }

    @Test
    @DisplayName("응답에 있는 날짜는 그대로 공휴일로 센다")
    void countStandardWorkingDays_countsEveryReturnedDate() {
        // 2026-07-17 제헌절(금요일): 2026년부터 공휴일로 지정되어 getRestDeInfo에 나타난다.
        // 연도별 공휴일 지정 여부는 API가 판단하고, 우리는 응답을 그대로 신뢰한다.
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20260717")
        ));

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2026, 7));

        // 2026년 7월 평일 23일 − 제헌절 1일.
        assertThat(numberOfStandardWorkingDays).isEqualTo(22L);
    }

    @Test
    @DisplayName("주말과 겹치는 공휴일은 소정근로일에서 차감하지 않는다")
    void countStandardWorkingDays_ignoresHolidaysOnWeekend() {
        // 2024-06-06 현충일은 목요일, 2024-06-01은 토요일.
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20240606"),
                HolidayResponseFixture.holiday("20240601")
        ));

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 6));

        assertThat(numberOfStandardWorkingDays).isEqualTo(19L);
    }

    @Test
    @DisplayName("fetchHolidays는 검증을 통과한 날짜와 이름을 반환한다 — 원장 동기화의 입력")
    void fetchHolidays_returnsDatesWithNames() {
        mockApiResponse(HolidayResponseFixture.normalResponse(
                HolidayResponseFixture.holiday("20260717", "제헌절")
        ));

        List<HolidayApiItem> items = apiConvertor.fetchHolidays(YearMonth.of(2026, 7));

        assertThat(items).containsExactly(new HolidayApiItem(LocalDate.of(2026, 7, 17), "제헌절"));
    }

    private void mockApiResponse(HolidayResponse response) {
        when(apiProperties.combineURL(any(), any()))
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
