package com.company.officecommute.web;

import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Component
public class HolidayApiClient {

    private static final Logger log = LoggerFactory.getLogger(HolidayApiClient.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String HOLIDAY_DATA_UNAVAILABLE_MESSAGE =
            "공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다. 잠시 후 다시 시도해 주세요.";
    private static final String NORMAL_RESULT_CODE = "00";
    // 월 단위 조회지만 numOfRows 기본값(10)에 의존하면 대체공휴일이 겹치는 달에서 응답이 잘릴 수 있다.
    private static final int NUM_OF_ROWS = 100;

    private final RestTemplate restTemplate;
    private final HolidayApiProperties properties;

    public HolidayApiClient(
            RestTemplate restTemplate,
            HolidayApiProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public Set<LocalDate> getHolidays(YearMonth yearMonth) {
        try {
            List<HolidayResponse.Item> items = fetchHolidaysFromApi(yearMonth);
            Set<LocalDate> holidays = convertToLocalDate(items);
            log.info("공휴일 API 호출 성공: {}-{}, 공휴일 {}일", yearMonth.getYear(), yearMonth.getMonthValue(), holidays.size());
            return holidays;
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
        URI uri = buildRequestUri(yearMonth);

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

    private URI buildRequestUri(YearMonth yearMonth) {
        return UriComponentsBuilder.fromUriString(properties.getUrl())
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("solYear", yearMonth.getYear())
                .queryParam("solMonth", String.format("%02d", yearMonth.getMonthValue()))
                .queryParam("numOfRows", NUM_OF_ROWS)
                .build(true)
                .toUri();
    }

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

    private void validateResponseCount(Integer totalCount, int receivedCount, YearMonth yearMonth) {
        if (totalCount == null || totalCount != receivedCount) {
            throw new HolidayDataUnavailableException(
                    "공휴일 API 응답 건수가 일치하지 않습니다. yearMonth=" + yearMonth
                            + ", totalCount=" + totalCount + ", received=" + receivedCount);
        }
    }

    private Set<LocalDate> convertToLocalDate(List<HolidayResponse.Item> items) {
        return items.stream()
                .map(item -> LocalDate.parse(item.getLocdate(), DATE_FORMATTER))
                .collect(toSet());
    }

}
