package com.company.officecommute.web;

import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Component
public class ApiConvertor {

    private static final Logger log = LoggerFactory.getLogger(ApiConvertor.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String HOLIDAY_DATA_UNAVAILABLE_MESSAGE =
            "공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다. 잠시 후 다시 시도해 주세요.";
    private static final String NORMAL_RESULT_CODE = "00";

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public ApiConvertor(
            RestTemplate restTemplate,
            ApiProperties apiProperties
    ) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public long countNumberOfStandardWorkingDays(YearMonth yearMonth) {
        Set<LocalDate> holidays = fetchHolidays(yearMonth).stream()
                .map(HolidayApiItem::date)
                .collect(toSet());
        long numberOfWeekDays = getNumberOfWeekDays(yearMonth);
        long numberOfHolidays = countWeekdayHolidays(holidays);

        return numberOfWeekDays - numberOfHolidays;
    }

    private static long getNumberOfWeekDays(YearMonth yearMonth) {
        int lengthOfMonth = yearMonth.lengthOfMonth();
        long numberOfWeekends = WeekendCalculator.countNumberOfWeekends(yearMonth);
        return lengthOfMonth - numberOfWeekends;
    }

    /**
     * 해당 월의 공휴일을 조회해 검증을 통과한 항목만 반환한다. 원장 동기화와 소정근로일 계산이
     * 공유하는 단일 경로로, 실패는 전부 {@link HolidayDataUnavailableException}으로 수렴한다.
     */
    public List<HolidayApiItem> fetchHolidays(YearMonth yearMonth) {
        try {
            List<HolidayResponse.Item> rawItems = fetchHolidaysFromApi(yearMonth);
            List<HolidayApiItem> items = convertToApiItems(rawItems, yearMonth);
            log.info("공휴일 API 호출 성공: {}-{}, 공휴일 {}일", yearMonth.getYear(), yearMonth.getMonthValue(), items.size());
            return items;
        } catch (HolidayDataUnavailableException e) {
            // 응답 검증에서 잡아낸 구체적 사유는 일반 메시지로 덮지 않는다.
            log.warn("공휴일 응답이 유효하지 않습니다. 리포트 생성을 중단합니다. yearMonth={}, reason={}",
                    yearMonth, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("공휴일 API 호출 실패. 리포트 생성을 중단합니다. yearMonth={}, error={}",
                    yearMonth, e.getMessage(), e);
            throw new HolidayDataUnavailableException(HOLIDAY_DATA_UNAVAILABLE_MESSAGE);
        }
    }

    private List<HolidayResponse.Item> fetchHolidaysFromApi(YearMonth yearMonth) {
        String solYear = String.valueOf(yearMonth.getYear());
        String solMonth = String.format("%02d", yearMonth.getMonthValue());
        String stringURL = apiProperties.combineURL(solYear, solMonth);
        URI uri;
        try {
            uri = new URI(stringURL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        HolidayResponse holidayResponse = restTemplate.getForObject(uri, HolidayResponse.class);
        if (holidayResponse == null) {
            throw new HolidayDataUnavailableException("공휴일 API 응답이 비어 있습니다. yearMonth=" + yearMonth);
        }
        validateResultCode(holidayResponse.getHeader(), yearMonth);

        HolidayResponse.Body body = holidayResponse.getBody();
        if (body == null) {
            throw new HolidayDataUnavailableException("공휴일 API 응답에 body가 없습니다. yearMonth=" + yearMonth);
        }
        // 공휴일이 없는 달(예: 4월, 11월)은 items 없음 + totalCount=0인 정상 응답이다.
        List<HolidayResponse.Item> items = body.getItems() != null ? body.getItems() : List.of();
        validateResponseCount(body.getTotalCount(), items.size(), yearMonth);
        return items;
    }

    /**
     * 공공데이터포털은 서비스키 미등록·트래픽 초과 같은 실패도 HTTP 200 + XML 본문으로 반환한다.
     * resultCode를 검증하지 않으면 그 응답이 "공휴일 0개"로 해석되어 소정근로시간이 과대 계산되고,
     * 결과적으로 초과근무가 과소 집계된다.
     */
    private void validateResultCode(HolidayResponse.Header header, YearMonth yearMonth) {
        if (header == null || header.getResultCode() == null) {
            throw new HolidayDataUnavailableException("공휴일 API 응답에 resultCode가 없습니다. yearMonth=" + yearMonth);
        }
        if (!NORMAL_RESULT_CODE.equals(header.getResultCode().trim())) {
            throw new HolidayDataUnavailableException(
                    "공휴일 API가 오류를 반환했습니다. yearMonth=" + yearMonth
                            + ", resultCode=" + header.getResultCode()
                            + ", resultMsg=" + header.getResultMsg());
        }
    }

    /**
     * 정상 응답은 항상 totalCount를 포함하며 실제 수신 건수와 일치한다.
     * 공휴일이 없는 달도 totalCount=0으로 온다.
     * <p>
     * totalCount가 없으면 응답의 완전성을 확인할 수 없고, 수신 건수보다 크면 페이지 크기를 넘겨
     * 잘린 것이다. 어느 쪽이든 조용히 넘기면 공휴일이 누락되어 초과근무가 과소 집계되므로
     * 건수가 정확히 일치하지 않는 응답으로는 계산하지 않는다.
     */
    private void validateResponseCount(Integer totalCount, int receivedCount, YearMonth yearMonth) {
        if (totalCount == null || totalCount != receivedCount) {
            throw new HolidayDataUnavailableException(
                    "공휴일 API 응답 건수가 일치하지 않습니다. yearMonth=" + yearMonth
                            + ", totalCount=" + totalCount + ", received=" + receivedCount);
        }
    }

    private long countWeekdayHolidays(Set<LocalDate> holidays) {
        return holidays.stream()
                .filter(date -> !WeekendCalculator.isWeekend(date))
                .count();
    }

    /**
     * getRestDeInfo(공휴일 정보조회)가 반환하는 항목은 정의상 모두 공휴일이므로 그대로 받아들인다.
     * 국경일 조회는 별도 엔드포인트(getHoliDeInfo)이고, 제헌절처럼 연도에 따라 공휴일 지정이
     * 바뀌는 날도 지정된 연도에만 이 응답에 나타난다. 즉 공휴일 여부 판단은 API의 책임이다.
     * <p>
     * 원장에 적재되는 데이터이므로 항목의 완전성(locdate 형식·요청 월 일치·dateName 존재)을
     * 검증하고, 어긋나면 조용히 건너뛰는 대신 실패시킨다.
     */
    private List<HolidayApiItem> convertToApiItems(List<HolidayResponse.Item> items, YearMonth yearMonth) {
        return items.stream()
                .map(item -> toApiItem(item, yearMonth))
                .toList();
    }

    private HolidayApiItem toApiItem(HolidayResponse.Item item, YearMonth yearMonth) {
        LocalDate date;
        try {
            date = LocalDate.parse(item.getLocdate(), DATE_FORMATTER);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new HolidayDataUnavailableException(
                    "공휴일 API 응답의 locdate를 해석할 수 없습니다. yearMonth=" + yearMonth
                            + ", locdate=" + item.getLocdate());
        }
        if (!YearMonth.from(date).equals(yearMonth)) {
            throw new HolidayDataUnavailableException(
                    "공휴일 API 응답에 요청한 월 밖의 날짜가 있습니다. yearMonth=" + yearMonth
                            + ", locdate=" + item.getLocdate());
        }
        if (item.getDateName() == null || item.getDateName().isBlank()) {
            throw new HolidayDataUnavailableException(
                    "공휴일 API 응답에 dateName이 없습니다. yearMonth=" + yearMonth
                            + ", locdate=" + item.getLocdate());
        }
        return new HolidayApiItem(date, item.getDateName().trim());
    }

    public long calculateStandardWorkingMinutes(long numberOfStandardWorkingDays) {
        return numberOfStandardWorkingDays * 8 * 60;
    }

}
