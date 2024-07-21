package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.HolidayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebMvcTest(ApiConvertor.class)
class ApiConvertor1Test {

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private ApiProperties apiProperties;

    @Autowired
    private ApiConvertor apiConvertor;

    @Test
    void _2024년_5월의_기준_근로_시간을_구하는_메서드를_검증하라() {
        ApiProperties fakeApiProperties = new TestApi();
        HolidayResponse fakeResponse = new HolidayResponse();
        // fakeItems에 필요한 가짜 데이터 추가
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item date_2024_05_05 = new HolidayResponse.Item();
        HolidayResponse.Item date_2024_05_06 = new HolidayResponse.Item();
        HolidayResponse.Item date_2024_05_15 = new HolidayResponse.Item();

        // 실제의 값들을 지정해줌으로써 언제든지 테스트가 성공하도록 한다
        date_2024_05_05.setLocDate("20240505");
        date_2024_05_06.setLocDate("20240506");
        date_2024_05_15.setLocDate("20240515");

        List<HolidayResponse.Item> fakeItems = List.of(date_2024_05_05, date_2024_05_06, date_2024_05_15);
        body.setItems(fakeItems);
        fakeResponse.setBody(body);

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(fakeResponse);

        ApiConvertor apiConvertor = new ApiConvertor(restTemplate, fakeApiProperties);
        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 5));

        assertThat(numberOfStandardWorkingDays).isEqualTo(21L);
    }
}
