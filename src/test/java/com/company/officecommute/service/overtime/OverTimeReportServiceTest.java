package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.employee.EmployeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OverTimeReportServiceTest {

    @InjectMocks
    private OverTimeReportService overTimeReportService;
    
    @Mock
    private OverTimeService overTimeService;
    
    @Mock
    private EmployeeRepository employeeRepository;

    private Employee employee1;
    private Employee employee2;

    @BeforeEach
    void setUp() {
        Team backendTeam = new Team("백엔드팀");
        Team frontendTeam = new Team("프론트엔드팀");
        
        employee1 = new EmployeeBuilder()
                .withId(1L)
                .withName("임형준")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP001")
                .withPassword("password123!")
                .withTeam(backendTeam)
                .build();
        
        employee2 = new EmployeeBuilder()
                .withId(2L)
                .withName("김개발")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1995, 5, 10))
                .withStartDate(LocalDate.of(2024, 2, 1))
                .withEmployeeCode("EMP002")
                .withPassword("password456!")
                .withTeam(frontendTeam)
                .build();
    }

    @Test
    @DisplayName("초과근무 보고서 데이터를 정상적으로 생성한다")
    void generateOverTimeReportData_success() {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "임형준", 300L), // 5시간 초과근무
                new OverTimeCalculateResponse(2L, "김개발", 120L)  // 2시간 초과근무
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee1));
        BDDMockito.given(employeeRepository.findById(2L))
                .willReturn(Optional.of(employee2));

        // when
        List<OverTimeReportData> result = overTimeReportService.generateOverTimeReportData(yearMonth);

        // then
        assertThat(result).hasSize(2);
        
        OverTimeReportData reportData1 = result.get(0);
        assertThat(reportData1.employeeName()).isEqualTo("임형준");
        assertThat(reportData1.teamName()).isEqualTo("백엔드팀");
        assertThat(reportData1.overTimeMinutes()).isEqualTo(300L);
        assertThat(reportData1.overTimePay()).isEqualTo(75000L); // 5시간 * 15000원
        
        OverTimeReportData reportData2 = result.get(1);
        assertThat(reportData2.employeeName()).isEqualTo("김개발");
        assertThat(reportData2.teamName()).isEqualTo("프론트엔드팀");
        assertThat(reportData2.overTimeMinutes()).isEqualTo(120L);
        assertThat(reportData2.overTimePay()).isEqualTo(30000L); // 2시간 * 15000원
    }

    @Test
    @DisplayName("팀이 배정되지 않은 직원은 '미배정'으로 표시된다")
    void generateOverTimeReportData_withUnassignedTeam() {
        // given
        Employee employeeWithoutTeam = new EmployeeBuilder()
                .withId(3L)
                .withName("박미배정")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP003")
                .withPassword("password789!")
                .withTeam(null)
                .build();
        
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(3L, "박미배정", 60L)
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);
        BDDMockito.given(employeeRepository.findById(3L))
                .willReturn(Optional.of(employeeWithoutTeam));

        // when
        List<OverTimeReportData> result = overTimeReportService.generateOverTimeReportData(yearMonth);

        // then
        assertThat(result).hasSize(1);
        OverTimeReportData reportData = result.get(0);
        assertThat(reportData.teamName()).isEqualTo("미배정");
    }

    @Test
    @DisplayName("존재하지 않는 직원 ID로 조회 시 예외가 발생한다")
    void generateOverTimeReportData_employeeNotFound() {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(999L, "존재하지않음", 60L)
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);
        BDDMockito.given(employeeRepository.findById(999L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> overTimeReportService.generateOverTimeReportData(yearMonth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 직원입니다.");
    }

    @Test
    @DisplayName("엑셀 파일이 정상적으로 생성된다")
    void generateExcelReport_success() throws IOException {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "임형준", 300L),
                new OverTimeCalculateResponse(2L, "김개발", 120L)
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee1));
        BDDMockito.given(employeeRepository.findById(2L))
                .willReturn(Optional.of(employee2));

        // when
        byte[] excelData = overTimeReportService.generateExcelReport(yearMonth);

        // then
        assertThat(excelData).isNotNull();
        assertThat(excelData.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("초과근무 시간이 0분인 경우 수당도 0원이다")
    void generateOverTimeReportData_zeroOvertime() {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "임형준", 0L)
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee1));

        // when
        List<OverTimeReportData> result = overTimeReportService.generateOverTimeReportData(yearMonth);

        // then
        assertThat(result).hasSize(1);
        OverTimeReportData reportData = result.get(0);
        assertThat(reportData.overTimeMinutes()).isEqualTo(0L);
        assertThat(reportData.overTimePay()).isEqualTo(0L);
    }

    @Test
    @DisplayName("59분 초과근무는 0시간으로 계산되어 수당이 0원이다")
    void generateOverTimeReportData_lessThanOneHour() {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "임형준", 59L) // 59분
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee1));

        // when
        List<OverTimeReportData> result = overTimeReportService.generateOverTimeReportData(yearMonth);

        // then
        assertThat(result).hasSize(1);
        OverTimeReportData reportData = result.get(0);
        assertThat(reportData.overTimeMinutes()).isEqualTo(59L);
        assertThat(reportData.overTimePay()).isEqualTo(0L); // 0시간 * 15000원 = 0원
    }
}
