package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.Holiday;
import com.company.officecommute.domain.overtime.HolidayResponse;
import com.company.officecommute.repository.overtime.HolidayRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorCircuitBreakerTest {

    @Autowired
    private ApiConvertor apiConvertor;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private RestTemplate restTemplate;
    @MockitoBean
    private ApiProperties apiProperties;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("holidayApi");
        circuitBreaker.reset();

        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");
    }

    @Test
    @DisplayName("API 호출 실패 시 Circuit Breaker fallback이 실행되어 DB에서 조회한다")
    void circuitBreakerFallback_whenApiFails_shouldQueryDatabase() {
        YearMonth yearMonth = YearMonth.of(2025, 12);

        // DB에 캐시 데이터 저장
        Holiday holiday = new Holiday(2025, 12, LocalDate.of(2025, 12, 25));
        holidayRepository.save(holiday);

        // API 호출 실패 시뮬레이션
        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenThrow(HttpServerErrorException.InternalServerError.create(
                        "Internal Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        null, null, null
                ));

        // Circuit Breaker fallback이 실행되어 DB에서 조회
        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        assertThat(workingDays).isGreaterThan(0);
    }

    @Test
    @DisplayName("연속 실패 시 Circuit이 Open 상태로 전환된다")
    void circuitOpens_afterConsecutiveFailures() {
        YearMonth yearMonth = YearMonth.of(2025, 11);

        // DB에 캐시 데이터 저장 (fallback용)
        // Holiday holiday = new Holiday(2025, 11, LocalDate.of(2025, 11, 3));
        // holidayRepository.save(holiday);

        // API 호출 실패 시뮬레이션
        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenThrow(HttpServerErrorException.InternalServerError.create(
                        "Internal Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        null, null, null
                ));

        // 초기 상태는 CLOSED
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 여러 번 호출하여 실패율을 높임 (sliding-window-size=10, minimum-number-of-calls=10)
        // 11번째 호출에서 실패율이 계산되어 Circuit이 Open됨
        for (int i = 0; i < 11; i++) {
            try {
                apiConvertor.countNumberOfStandardWorkingDays(yearMonth);
            } catch (Exception ignored) {
            }
        }

        // 연속 실패 후 Circuit이 OPEN 상태로 전환됨
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0f);
    }
}
