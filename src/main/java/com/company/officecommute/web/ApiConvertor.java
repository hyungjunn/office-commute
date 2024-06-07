package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.HolidayResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.YearMonth;
import java.util.List;

@Component
public class ApiConvertor {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public ApiConvertor(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public long getNumberOfHolidays(YearMonth yearMonth) throws MalformedURLException, URISyntaxException {
        // 공공데이터포털 사이트에서 주말과 법정 공휴일 데이터를 가지고 온다
        String solYear = String.valueOf(yearMonth.getYear());

        int month = yearMonth.getMonthValue();
        String solMonth = (month < 10) ? "0" + month : String.valueOf(month);

        String stringURL = apiProperties.combineURL(solYear, solMonth);
        URL url = new URL(stringURL);

        HolidayResponse holidayResponse = restTemplate.getForObject(url.toURI(), HolidayResponse.class);
        List<HolidayResponse.Item> items = holidayResponse.getBody().getItems();
        return items.size();
    }

}
