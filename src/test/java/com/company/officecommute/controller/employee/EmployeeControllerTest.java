package com.company.officecommute.controller.employee;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.employee.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class EmployeeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("유효하지 않은 값들로 직원 등록 요청 시 예외 발생")
    void testValidInputFailsValidation() {
        // 통제변인: role = "MEMBER", employeeCode = "E00001", password = "password123!" (정상)
        String invalidRequest = """
                    {
                        "name": "",
                        "role": "MEMBER",
                        "birthday": "2030-01-01",
                        "workStartDate": "2099-12-31",
                        "employeeCode": "E00001",
                        "password": "password123!"
                    }
                """;

        assertThat(mockMvcTester
                .post()
                .uri("/employee")
                .sessionAttr("employeeId", 1L)
                .sessionAttr("employeeRole", Role.MANAGER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "message": "입력값이 올바르지 않습니다",
                                "fieldErrorResults": [
                                    { "field": "name", "message": "직원 이름은 필수입니다." },
                                    { "field": "birthday", "message": "생일은 과거 날짜여야 합니다." },
                                    { "field": "workStartDate", "message": "입사일은 오늘이거나 과거 날짜여야 합니다." }
                                ]
                            }
                        """);
    }

    @Test
    @DisplayName("존재하지 않는 역할 값 입력 시 예외 발생")
    void testInvalidEnumFailsJsonParsing() {
        // JSON 파싱 실패 → INVALID_JSON
        // 통제변인: 나머지 필드 정상
        String invalidEnumRequest = """
                    {
                        "name": "John",
                        "role": "CEO",
                        "birthday": "1990-01-01",
                        "workStartDate": "2020-01-01"
                    }
                """;

        assertThat(mockMvcTester
                .post()
                .uri("/employee")
                .sessionAttr("employeeId", 1L)
                .sessionAttr("employeeRole", Role.MANAGER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidEnumRequest))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                            {
                                "code": "INVALID_JSON",
                                "message": "역할 값이 올바르지 않습니다."
                            }
                        """);
    }

    @Test
    @DisplayName("존재하지 않는 팀 배정시 예외 발생")
    void update_nonExistTeam() {
        doThrow(new IllegalArgumentException("해당하는 팀명(없는팀)이 없습니다."))
                .when(employeeService).updateEmployeeTeamName(any());

        assertThat(mockMvcTester.put().uri("/employee")
                .sessionAttr("employeeId", 1L)
                .sessionAttr("employeeRole", Role.MANAGER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "employeeId": 1,
                            "teamName": "없는팀"
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("해당하는 팀명(없는팀)이 없습니다.");
    }
}
