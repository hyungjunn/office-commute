package com.company.officecommute.web;

import com.company.officecommute.config.RestTemplateConfig;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 와이어 경계 테스트: HolidayApiClientTest는 RestTemplate을 목으로 두어 XML 역직렬화를
 * 거치지 않으므로, 여기서는 실제 RestTemplateConfig의 컨버터 체인에 공공데이터포털의
 * 실제 응답 원문(2026-08-05 캡처)을 통과시켜 HTTP 200 + XML 에러가 fail-closed로
 * 이어지는지 고정한다.
 */
class HolidayApiClientWireTest {

    // 2026-08-05 잘못된 serviceKey로 실측 캡처한 게이트웨이 에러 원문 (현재는 HTTP 403으로 오지만,
    // 과거·타 서비스에서 HTTP 200으로 오던 모양이므로 200 케이스도 함께 검증한다).
    private static final String GATEWAY_ERROR_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OpenAPI_ServiceResponse>
            <cmmMsgHeader>
              <errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
              <returnAuthMsg>등록되지 않은 서비스키</returnAuthMsg>
              <returnReasonCode>30</returnReasonCode>
            </cmmMsgHeader>
            </OpenAPI_ServiceResponse>
            """;

    // 공공데이터포털 문서상 애플리케이션 레벨 에러 모양 (정상 envelope + resultCode != 00).
    private static final String APPLICATION_ERROR_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <response><header><resultCode>22</resultCode><resultMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</resultMsg></header></response>
            """;

    // 2026-08-05 실측 캡처한 정상 응답 원문 일부 (dateKind·dateName·seq 등 매핑에 없는 필드 포함).
    private static final String SUCCESS_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header><body><items><item><dateKind>01</dateKind><dateName>제헌절</dateName><isHoliday>Y</isHoliday><locdate>20260717</locdate><seq>1</seq></item></items><numOfRows>100</numOfRows><pageNo>1</pageNo><totalCount>1</totalCount></body></response>
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HolidayApiClient client;

    @BeforeEach
    void setUp() {
        HolidayApiProperties properties = new HolidayApiProperties();
        properties.setUrl("https://fake-api.com/getRestDeInfo");
        properties.setServiceKey("service%2Bkey");

        restTemplate = new RestTemplateConfig(properties).restTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        client = new HolidayApiClient(restTemplate, properties);
    }

    @Test
    @DisplayName("HTTP 200 + OpenAPI_ServiceResponse 게이트웨이 에러 XML은 계산에 쓰이지 않고 중단된다")
    void http200_gatewayErrorXml_failsClosed() {
        server.expect(anything())
                .andRespond(withSuccess(GATEWAY_ERROR_XML, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.getHolidays(YearMonth.of(2026, 7)))
                .isInstanceOf(HolidayDataUnavailableException.class);
    }

    @Test
    @DisplayName("HTTP 200 + resultCode!=00 애플리케이션 에러 XML은 사유와 함께 중단된다")
    void http200_applicationErrorXml_failsClosed() {
        server.expect(anything())
                .andRespond(withSuccess(APPLICATION_ERROR_XML, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.getHolidays(YearMonth.of(2026, 7)))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");
    }

    @Test
    @DisplayName("HTTP 403 + 게이트웨이 에러 XML(2026-08 실측 현행)도 중단된다")
    void http403_gatewayErrorXml_failsClosed() {
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_XML)
                        .body(GATEWAY_ERROR_XML));

        assertThatThrownBy(() -> client.getHolidays(YearMonth.of(2026, 7)))
                .isInstanceOf(HolidayDataUnavailableException.class);
    }

    @Test
    @DisplayName("실측 정상 응답 원문은 매핑에 없는 필드가 있어도 정상 파싱된다")
    void http200_realSuccessXml_parses() {
        server.expect(anything())
                .andRespond(withSuccess(SUCCESS_XML, MediaType.APPLICATION_XML));

        Set<LocalDate> holidays = client.getHolidays(YearMonth.of(2026, 7));

        assertThat(holidays).containsExactly(LocalDate.of(2026, 7, 17));
    }
}
