package com.company.officecommute.controller.holiday;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.service.holiday.HolidaySyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
class HolidayControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private HolidaySyncService holidaySyncService;

    @Nested
    @DisplayName("POST /holiday/sync")
    class SyncHolidaysTests {

        @Test
        @DisplayName("MANAGER가 연도를 지정하면 그 해를 동기화하고 적재 건수를 돌려준다")
        void syncHolidays_authorized() {
            given(holidaySyncService.syncYear(Year.of(2026))).willReturn(22);

            assertThat(mockMvcTester
                    .post()
                    .uri("/holiday/sync?year=2026")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "year": 2026,
                                "holidayCount": 22
                            }
                            """);
        }

        @Test
        @DisplayName("MEMBER가 동기화를 걸면 403이고 원장은 건드리지 않는다")
        void syncHolidays_forbiddenForMember() {
            assertThat(mockMvcTester
                    .post()
                    .uri("/holiday/sync?year=2026")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("FORBIDDEN");

            verify(holidaySyncService, never()).syncYear(any());
        }

        @Test
        @DisplayName("비로그인 요청은 401이다")
        void syncHolidays_unauthorized() {
            assertThat(mockMvcTester
                    .post()
                    .uri("/holiday/sync?year=2026"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        }

        /**
         * 정기 동기화는 미발표 연도를 조용히 넘기지만, 관리자가 직접 부른 요청은 결과를 알아야 한다.
         */
        @Test
        @DisplayName("동기화가 실패하면 503으로 사유를 알린다 — 관리자 요청은 실패를 삼키지 않는다")
        void syncHolidays_reportsFailure() {
            willThrow(new HolidayDataUnavailableException("공휴일 API가 해당 연도에 0건을 반환했습니다."))
                    .given(holidaySyncService).syncYear(Year.of(2099));

            assertThat(mockMvcTester
                    .post()
                    .uri("/holiday/sync?year=2099")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("HOLIDAY_DATA_UNAVAILABLE");
        }

        @Test
        @DisplayName("범위 밖 연도는 외부 API를 부르기 전에 400으로 막는다")
        void syncHolidays_rejectsYearOutOfRange() {
            assertThat(mockMvcTester
                    .post()
                    .uri("/holiday/sync?year=1999")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("VALIDATION_ERROR");

            verify(holidaySyncService, never()).syncYear(any());
        }

        @Test
        @DisplayName("year 파라미터가 없으면 400이다")
        void syncHolidays_rejectsMissingYear() {
            assertThat(mockMvcTester
                    .post()
                    .uri("/holiday/sync")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }
    }

    private MockHttpSession managerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 1L);
        session.setAttribute("currentRole", Role.MANAGER);
        return session;
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 2L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
