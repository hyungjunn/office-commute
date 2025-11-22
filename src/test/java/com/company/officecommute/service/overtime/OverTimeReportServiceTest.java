package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OverTimeReportServiceTest {

    @InjectMocks
    private OverTimeReportService overTimeReportService;
    
    @Mock
    private OverTimeService overTimeService;

    @Test
    @DisplayName("초과근무 보고서 데이터를 정상적으로 생성한다")
    void generateOverTimeReportData_success() {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 300L), // 5시간 초과근무
                new OverTimeCalculateResponse(2L, "김개발", "프론트엔드팀", 120L)  // 2시간 초과근무
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

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
    @DisplayName("엑셀 파일이 정상적으로 생성된다")
    void generateExcelReport_success() throws IOException {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 300L),
                new OverTimeCalculateResponse(2L, "김개발", "프론트엔드팀", 120L)
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

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
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 0L)
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        // when
        List<OverTimeReportData> result = overTimeReportService.generateOverTimeReportData(yearMonth);

        // then
        assertThat(result).hasSize(1);
        OverTimeReportData reportData = result.getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(0L);
        assertThat(reportData.overTimePay()).isEqualTo(0L);
    }

    @Test
    @DisplayName("59분 초과근무는 0시간으로 계산되어 수당이 0원이다")
    void generateOverTimeReportData_lessThanOneHour() {
        // given
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀",59L) // 59분
        );
        
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        // when
        List<OverTimeReportData> result = overTimeReportService.generateOverTimeReportData(yearMonth);

        // then
        assertThat(result).hasSize(1);
        OverTimeReportData reportData = result.getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(59L);
        assertThat(reportData.overTimePay()).isEqualTo(0L); // 0시간 * 15000원 = 0원
    }
}
