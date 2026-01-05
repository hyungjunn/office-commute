package com.company.officecommute.docs;

import com.company.officecommute.controller.employee.EmployeeController;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.service.employee.EmployeeService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmployeeControllerDocsTest extends RestDocsSupport {

    private final EmployeeService employeeService = mock(EmployeeService.class);

    @Override
    protected Object initController() {
        return new EmployeeController(employeeService);
    }

    @Test
    @DisplayName("로그인 API")
    void login() throws Exception {
        Employee employee = new Employee(
                1L, null, "홍길동", null, Role.MEMBER,
                LocalDate.of(1990, 1, 1),
                LocalDate.of(2020, 3, 1),
                "EMP001", "encodedPassword"
        );
        given(employeeService.authenticate(anyString(), anyString())).willReturn(employee);

        String request = """
                {
                    "employeeCode": "EMP001",
                    "password": "password123!"
                }
                """;

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-login",
                        requestFields(
                                fieldWithPath("employeeCode").type(JsonFieldType.STRING)
                                        .description("사번"),
                                fieldWithPath("password").type(JsonFieldType.STRING)
                                        .description("비밀번호")
                        )
                ));
    }

    @Test
    @DisplayName("로그아웃 API")
    void logout() throws Exception {
        mockMvc.perform(post("/logout")
                        .sessionAttr("employeeId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-logout"));
    }

    @Test
    @DisplayName("직원 등록 API")
    void saveEmployee() throws Exception {
        doNothing().when(employeeService).registerEmployee(any(EmployeeSaveRequest.class));

        String request = """
                {
                    "name": "홍길동",
                    "role": "MEMBER",
                    "birthday": "1990-01-15",
                    "workStartDate": "2024-01-02",
                    "employeeCode": "EMP002",
                    "password": "Password1!"
                }
                """;

        mockMvc.perform(post("/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .sessionAttr("employeeId", 1L)
                        .sessionAttr("employeeRole", Role.MANAGER)
                        .content(request))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-save",
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING)
                                        .description("직원 이름"),
                                fieldWithPath("role").type(JsonFieldType.STRING)
                                        .description("역할 (MANAGER, MEMBER)"),
                                fieldWithPath("birthday").type(JsonFieldType.STRING)
                                        .description("생년월일 (yyyy-MM-dd)"),
                                fieldWithPath("workStartDate").type(JsonFieldType.STRING)
                                        .description("입사일 (yyyy-MM-dd)"),
                                fieldWithPath("employeeCode").type(JsonFieldType.STRING)
                                        .description("사번 (대문자+숫자 6-10자리)"),
                                fieldWithPath("password").type(JsonFieldType.STRING)
                                        .description("비밀번호 (대소문자, 숫자 포함 8자 이상)")
                        )
                ));
    }

    @Test
    @DisplayName("직원 전체 조회 API")
    void findAllEmployee() throws Exception {
        List<EmployeeFindResponse> responses = List.of(
                new EmployeeFindResponse("홍길동", "개발팀", "MANAGER", "1990-01-15", "2020-03-01"),
                new EmployeeFindResponse("김철수", "개발팀", "MEMBER", "1995-05-20", "2023-01-02")
        );
        given(employeeService.findAllEmployee()).willReturn(responses);

        mockMvc.perform(get("/employee")
                        .accept(MediaType.APPLICATION_JSON)
                        .sessionAttr("employeeId", 1L)
                        .sessionAttr("employeeRole", Role.MANAGER))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-find-all",
                        responseFields(
                                fieldWithPath("[].name").type(JsonFieldType.STRING)
                                        .description("직원 이름"),
                                fieldWithPath("[].teamName").type(JsonFieldType.STRING)
                                        .description("소속 팀 이름"),
                                fieldWithPath("[].role").type(JsonFieldType.STRING)
                                        .description("역할"),
                                fieldWithPath("[].birthday").type(JsonFieldType.STRING)
                                        .description("생년월일"),
                                fieldWithPath("[].workStartDate").type(JsonFieldType.STRING)
                                        .description("입사일")
                        )
                ));
    }

    @Test
    @DisplayName("직원 팀 변경 API")
    void updateEmployeeTeamName() throws Exception {
        doNothing().when(employeeService).updateEmployeeTeamName(any(EmployeeUpdateTeamNameRequest.class));

        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "기획팀");

        mockMvc.perform(put("/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .sessionAttr("employeeId", 1L)
                        .sessionAttr("employeeRole", Role.MANAGER)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-update-team",
                        requestFields(
                                fieldWithPath("employeeId").type(JsonFieldType.NUMBER)
                                        .description("직원 ID"),
                                fieldWithPath("teamName").type(JsonFieldType.STRING)
                                        .description("변경할 팀 이름")
                        )
                ));
    }
}
