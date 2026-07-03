package com.company.officecommute.controller.commute;

import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class CommuteHistoryControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private CommuteHistoryService commuteHistoryService;

    @Test
    @DisplayName("POST /commute — 같은 날 중복 출근이면 409 DUPLICATE_WORK")
    void registerWorkStartTime_duplicateWorkReturns409() {
        doThrow(new DuplicateWorkOnDateException(LocalDate.of(2026, 5, 23)))
                .when(commuteHistoryService).registerWorkStartTime(2L);

        assertThat(mockMvcTester
                .post()
                .uri("/commute")
                .session(memberSession()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "DUPLICATE_WORK",
                            "message": "해당 일자에 이미 출근 기록이 존재합니다: 2026-05-23"
                        }
                        """);
    }

    @Test
    @DisplayName("GET /commute — yearMonth로 조회하면 200과 월별 근무 시간을 반환한다")
    void getWorkDurationPerDate_returns200() {
        // given
        given(commuteHistoryService.getWorkDurationPerDate(2L, YearMonth.of(2026, 7)))
                .willReturn(new WorkDurationPerDateResponse(List.of(), 0L));

        // when / then
        assertThat(mockMvcTester
                .get()
                .uri("/commute?yearMonth=2026-07")
                .session(memberSession()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "details": [],
                            "sumWorkingMinutes": 0
                        }
                        """);
    }

    @Test
    @DisplayName("GET /commute — yearMonth가 없으면 400 MISSING_PARAMETER")
    void getWorkDurationPerDate_missingYearMonthReturns400() {
        // when / then
        assertThat(mockMvcTester
                .get()
                .uri("/commute")
                .session(memberSession()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "MISSING_PARAMETER",
                            "message": "필수 파라미터가 누락되었습니다: yearMonth"
                        }
                        """);

        then(commuteHistoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /commute — yearMonth 형식이 잘못되면 400 INVALID_PARAMETER")
    void getWorkDurationPerDate_invalidYearMonthReturns400() {
        // when / then
        assertThat(mockMvcTester
                .get()
                .uri("/commute?yearMonth=2026-13")
                .session(memberSession()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "INVALID_PARAMETER",
                            "message": "파라미터 형식이 올바르지 않습니다: yearMonth"
                        }
                        """);

        then(commuteHistoryService).shouldHaveNoInteractions();
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 2L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
