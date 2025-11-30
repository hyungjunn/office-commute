package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.Holiday;
import com.company.officecommute.domain.overtime.HolidayResponse;
import com.company.officecommute.repository.overtime.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Component
public class ApiConvertor {

    private static final Logger log = LoggerFactory.getLogger(ApiConvertor.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;
    private final HolidayRepository holidayRepository;

    public ApiConvertor(
            RestTemplate restTemplate,
            ApiProperties apiProperties,
            HolidayRepository holidayRepository
    ) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
        this.holidayRepository = holidayRepository;
    }

    @Transactional
    public long countNumberOfStandardWorkingDays(YearMonth yearMonth) {
        Set<LocalDate> holidays = getHolidays(yearMonth);
        int lengthOfMonth = yearMonth.lengthOfMonth();
        long numberOfWeekends = WeekendCalculator.countNumberOfWeekends(yearMonth);
        long numberOfWeekDays = lengthOfMonth - numberOfWeekends;
        long numberOfHolidays = minusDuplicateHolidays(holidays.size(), holidays);

        return numberOfWeekDays - numberOfHolidays;
    }

    private Set<LocalDate> getHolidays(YearMonth yearMonth) {
        try {
            List<HolidayResponse.Item> items = fetchHolidaysFromApi(yearMonth);
            Set<LocalDate> holidays = convertToLocalDate(items);
            saveHolidaysToDatabase(yearMonth, holidays);
            log.info("공휴일 API 호출 성공: {}-{}", yearMonth.getYear(), yearMonth.getMonthValue());
            return holidays;
        } catch (Exception e) {
            log.warn("공휴일 API 호출 실패. DB에서 조회합니다. 오류: {}", e.getMessage());
            List<LocalDate> cachedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(
                    yearMonth.getYear(),
                    yearMonth.getMonthValue()
            );

            if (cachedHolidays.isEmpty()) {
                log.warn("DB에도 공휴일 데이터가 없습니다: {}-{}", yearMonth.getYear(), yearMonth.getMonthValue());
            }

            return Set.copyOf(cachedHolidays);
        }
    }

    private void saveHolidaysToDatabase(YearMonth yearMonth, Set<LocalDate> holidays) {
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        holidayRepository.deleteByYearAndMonth(year, month);

        List<Holiday> holidayEntities = holidays.stream()
                .map(date -> new Holiday(year, month, date))
                .toList();
        holidayRepository.saveAll(holidayEntities);
    }

    private List<HolidayResponse.Item> fetchHolidaysFromApi(YearMonth yearMonth) {
        String solYear = String.valueOf(yearMonth.getYear());

        int month = yearMonth.getMonthValue();
        String solMonth = (month < 10) ? "0" + month : String.valueOf(month);

        String stringURL = apiProperties.combineURL(solYear, solMonth);
        URI uri;
        try {
            uri = new URI(stringURL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        HolidayResponse holidayResponse = restTemplate.getForObject(uri, HolidayResponse.class);
        if (holidayResponse == null || holidayResponse.getBody() == null) {
            log.warn("공휴일 API 응답이 비어있음. yearMonth={}", yearMonth);
            return List.of();
        }
        return holidayResponse.getBody().getItems();
    }

    private long minusDuplicateHolidays(long numberOfHolidays, Set<LocalDate> holidays) {
        numberOfHolidays -= holidays.stream()
                .filter(WeekendCalculator::isWeekend)
                .count();
        return numberOfHolidays;
    }

    private Set<LocalDate> convertToLocalDate(List<HolidayResponse.Item> items) {
        return items.stream()
                .map(item -> LocalDate.parse(item.getLocdate(), DATE_FORMATTER))
                .collect(toSet());
    }

    public long calculateStandardWorkingMinutes(long numberOfStandardWorkingDays) {
        return numberOfStandardWorkingDays * 8 * 60;
    }

    /**
     * 다음 달 공휴일을 미리 DB에 저장합니다.
     * 월말에 초과근무 계산 후 호출하여 다음 달 API 실패에 대비합니다.
     */
    @Transactional
    public void prefetchNextMonthHolidays(YearMonth currentMonth) {
        YearMonth nextMonth = currentMonth.plusMonths(1);
        try {
            List<HolidayResponse.Item> items = fetchHolidaysFromApi(nextMonth);
            Set<LocalDate> holidays = convertToLocalDate(items);
            saveHolidaysToDatabase(nextMonth, holidays);
            log.info("다음 달 공휴일 선제적 저장 성공: {}-{}", nextMonth.getYear(), nextMonth.getMonthValue());
        } catch (Exception e) {
            log.warn("다음 달 공휴일 선제적 저장 실패. 다음 달에 재시도합니다. 오류: {}", e.getMessage());
            // 실패해도 예외를 던지지 않음 (다음 달에 다시 시도할 수 있음)
        }
    }

}
