package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;

@Service
public class OverTimeReportService {

    private final OverTimeService overTimeService;
    private final EmployeeRepository employeeRepository;
    
    private static final long HOURLY_OVERTIME_PAY = 15000; // 시간당 초과근무 수당 (임시)

    public OverTimeReportService(OverTimeService overTimeService, EmployeeRepository employeeRepository) {
        this.overTimeService = overTimeService;
        this.employeeRepository = employeeRepository;
    }

    public byte[] generateExcelReport(YearMonth yearMonth) throws IOException {
        List<OverTimeReportData> reportData = generateOverTimeReportData(yearMonth);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(yearMonth.getMonth() + "월 초과근무 보고서");
            createHeader(sheet);

            // 서식 준비: 시간/통화
            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle timeCellStyle = workbook.createCellStyle();
            timeCellStyle.setDataFormat(dataFormat.getFormat("[h]:mm")); // 24h 초과 시간 형식

            CellStyle currencyCellStyle = workbook.createCellStyle();
            currencyCellStyle.setDataFormat(dataFormat.getFormat("₩#,##0")); // 한국 원화 형식

            // 데이터 입력
            int rowNum = 1;
            for (OverTimeReportData data : reportData) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(data.employeeName());
                row.createCell(1).setCellValue(data.teamName());

                // 분 단위를 엑셀 시간으로 변환
                // excel에서는 1이 하루(24시간)이다.
                // excelTime을 x라고 할 때,
                // 24 * 60 : date.overTimeMinutes() = 1 : x
                double excelTime = data.overTimeMinutes() / (24d * 60d);
                Cell timeCell = row.createCell(2);
                timeCell.setCellValue(excelTime);
                timeCell.setCellStyle(timeCellStyle);

                Cell payCell = row.createCell(3);
                payCell.setCellValue(data.overTimePay());
                payCell.setCellStyle(currencyCellStyle);
            }

            // 데이터가 0행 헤더, 1행부터 데이터 → 마지막 데이터 행 번호 = reportData.size()
            int lastDataRow = reportData.size();     // 1-based 데이터 마지막 행 (헤더 제외)
            int totalRowIdx = lastDataRow + 1;       // 합계는 그 다음 행에
            Row totalRow = sheet.createRow(totalRowIdx);

            // "합계" 라벨
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("합계");

            // 총 시간: C2 ~ C{lastDataRow+1} (열 인덱스 2는 엑셀의 C열)
            Cell totalTime = totalRow.createCell(2);
            String timeRange = String.format("C2:C%d", lastDataRow + 1);
            totalTime.setCellFormula("SUM(" + timeRange + ")");
            totalTime.setCellStyle(timeCellStyle); // [h]:mm 로 표시

            // 총 수당: D2 ~ D{lastDataRow+1} (열 인덱스 3은 엑셀의 D열)
            Cell totalPay = totalRow.createCell(3);
            String payRange = String.format("D2:D%d", lastDataRow + 1);
            totalPay.setCellFormula("SUM(" + payRange + ")");
            totalPay.setCellStyle(currencyCellStyle);

            // 컬럼 너비 자동 조정
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public List<OverTimeReportData> generateOverTimeReportData(YearMonth yearMonth) {
        List<OverTimeCalculateResponse> overTimeData = overTimeService.calculateOverTime(yearMonth);

        return overTimeData.stream()
                .map(this::convertToReportData)
                .toList();
    }

    private OverTimeReportData convertToReportData(OverTimeCalculateResponse response) {
        // 초과근무 수당 계산 (분 단위 → 시간 단위 변환)
        long overTimeHours = response.overTimeMinutes() / 60;
        long overTimePay = overTimeHours * HOURLY_OVERTIME_PAY;

        return new OverTimeReportData(
                response.name(),
                response.teamName(),
                response.overTimeMinutes(),
                overTimePay
        );
    }

    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        
        // 헤더 스타일 생성
        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        String[] headers = {"직원명", "부서명", "초과근무시간", "초과근무수당"};
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
}
