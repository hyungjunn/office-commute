package com.company.officecommute.controller.overtime;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc
class OverTimeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private OverTimeService overTimeService;

    @MockitoBean
    private OverTimeReportService overTimeReportService;

    @Nested
    @DisplayName("초과근무 조회 API 테스트")
    class CalculateOverTimeTests {

        @Test
        @DisplayName("MANAGER 권한이 없는 경우 초과근무 조회 요청 시 예외 발생")
        void calculateOverTime_unauthorized() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MEMBER))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "FORBIDDEN",
                                "message": "관리자만 접근 가능"
                            }
                        """);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 초과근무 조회 성공")
        void calculateOverTime_authorized() {
            YearMonth yearMonth = YearMonth.of(2024, 8);
            List<OverTimeCalculateResponse> mockData = Arrays.asList(
                    new OverTimeCalculateResponse(1L, "임형준", 300L),
                    new OverTimeCalculateResponse(2L, "김개발", 120L)
            );
            
            given(overTimeService.calculateOverTime(yearMonth))
                    .willReturn(mockData);

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                                {
                                    "id": 1,
                                    "name": "임형준",
                                    "overTimeMinutes": 300
                                },
                                {
                                    "id": 2,
                                    "name": "김개발",
                                    "overTimeMinutes": 120
                                }
                            ]
                        """);
        }

        @Test
        @DisplayName("잘못된 yearMonth 형식으로 요청 시 예외 발생")
        void calculateOverTime_invalidYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=invalid-date")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("INVALID_PARAMETER");
        }

        @Test
        @DisplayName("yearMonth 파라미터가 누락된 경우 예외 발생")
        void calculateOverTime_missingYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }
    }

    @Nested
    @DisplayName("초과근무 엑셀 다운로드 API 테스트")
    class DownloadOverTimeReportTests {

        @Test
        @DisplayName("MANAGER 권한이 없는 경우 엑셀 다운로드 요청 시 예외 발생")
        void downloadOverTimeReport_unauthorized() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=2024-08")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MEMBER))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "FORBIDDEN",
                                "message": "관리자만 접근 가능"
                            }
                        """);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 엑셀 다운로드 성공")
        void downloadOverTimeReport_authorized() throws Exception {
            YearMonth yearMonth = YearMonth.of(2024, 8);
            byte[] mockExcelData = "mock excel data".getBytes();
            
            given(overTimeReportService.generateExcelReport(yearMonth))
                    .willReturn(mockExcelData);

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=2024-08")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.OK)
                    .headers()
                    .hasValue("Content-Type", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toString())
                    .satisfies(headers -> {
                        String cd = headers.getFirst("Content-Disposition");
                        assertThat(cd).startsWith("attachment");
                        assertThat(cd).contains("filename*=");
                        String fileName = "2024년8월_초과근무보고서.xlsx";
                        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                        assertThat(cd).contains(encoded);
                    });
        }

        @Test
        @DisplayName("잘못된 yearMonth 형식으로 엑셀 다운로드 요청 시 예외 발생")
        void downloadOverTimeReport_invalidYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=invalid-date")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("INVALID_PARAMETER");
        }

        @Test
        @DisplayName("yearMonth 파라미터가 누락된 경우 엑셀 다운로드 예외 발생")
        void downloadOverTimeReport_missingYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }

        @Test
        @DisplayName("엑셀 생성 중 IOException 발생 시 서버 에러 반환")
        void downloadOverTimeReport_ioException() throws Exception {
            YearMonth yearMonth = YearMonth.of(2024, 8);
            
            given(overTimeReportService.generateExcelReport(yearMonth))
                    .willThrow(new RuntimeException("엑셀 생성 실패"));

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=2024-08")
                    .sessionAttr("employeeId", 1L)
                    .sessionAttr("employeeRole", Role.MANAGER))
                    .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("세션 권한 테스트")
    class SessionAuthTests {

        @Test
        @DisplayName("employeeId 세션이 없는 경우 예외 발생")
        void noSessionAttribute() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
