package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.HolidayResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Component
public class ApiConvertor {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ApiConvertor(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    private List<HolidayResponse.Item> itemsOfResponse(YearMonth yearMonth) throws MalformedURLException, URISyntaxException {
        String solYear = String.valueOf(yearMonth.getYear());

        int month = yearMonth.getMonthValue();
        String solMonth = (month < 10) ? "0" + month : String.valueOf(month);

        String stringURL = apiProperties.combineURL(solYear, solMonth);
        URL url = new URL(stringURL);

        HolidayResponse holidayResponse = restTemplate.getForObject(url.toURI(), HolidayResponse.class);
        List<HolidayResponse.Item> items = holidayResponse.getBody().getItems();
        return items;
    }

    public long countNumberOfStandardWorkingDays(YearMonth yearMonth) throws MalformedURLException, URISyntaxException {
        List<HolidayResponse.Item> items = itemsOfResponse(yearMonth);

        int lengthOfMonth = yearMonth.lengthOfMonth();
        long numberOfWeekends = WeekendCalculator.countNumberOfWeekends(yearMonth);
        long numberOfHolidays = items.size();
        long numberOfWeekDays = lengthOfMonth - numberOfWeekends;

        Set<LocalDate> holidays = convertToLocalDate(items);
        numberOfHolidays = minusDuplicateHolidays(numberOfHolidays, holidays);

        return numberOfWeekDays - numberOfHolidays;
    }

    public long calculateStandardWorkingMinutes(long numberOfStandardWorkingDays) {
        return numberOfStandardWorkingDays * 8 * 60;
    }

    private static long minusDuplicateHolidays(long numberOfHolidays, Set<LocalDate> holidays) {
        numberOfHolidays -= holidays.stream()
                .filter(WeekendCalculator::isWeekend)
                .count();
        return numberOfHolidays;
    }

    private static Set<LocalDate> convertToLocalDate(List<HolidayResponse.Item> items) {
        return items.stream()
                .map(item -> LocalDate.parse(item.getLocdate(), DATE_FORMATTER))
                .collect(toSet());
    }

}
