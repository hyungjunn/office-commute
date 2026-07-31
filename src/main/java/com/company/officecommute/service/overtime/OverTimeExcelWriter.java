package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.List;

@Component
public class OverTimeExcelWriter {

    private static final int COL_EMPLOYEE_CODE = 0;
    private static final int COL_EMPLOYEE_NAME = 1;
    private static final int COL_TEAM_NAME = 2;
    private static final int COL_OVERTIME = 3;
    private static final int COL_PAY = 4;

    private static final String[] HEADERS = {"사번", "직원명", "부서명", "초과근무시간", "초과근무수당"};

    // 행 수가 직원 수로 묶여 있어(수천 행) 전체를 메모리에 들고 가는 XSSF로 충분하다.
    // SXSSF와 달리 수식 계산이 가능해, 합계 수식에 계산된 값까지 함께 저장할 수 있다.
    public void write(YearMonth yearMonth, List<OverTimeReportData> reportData, OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(yearMonth.getMonthValue() + "월 초과근무 보고서");
            setColumnWidths(sheet);
            createHeader(sheet);

            CellStyle timeCellStyle = createTimeCellStyle(workbook);
            CellStyle currencyCellStyle = createCurrencyCellStyle(workbook);
            createDataRows(sheet, reportData, timeCellStyle, currencyCellStyle);
            createTotalRow(sheet, reportData.size(), timeCellStyle, currencyCellStyle);

            // 수식만 저장하면 계산된 값이 없어, 열 때 재계산하지 않는 뷰어(메일 미리보기 등)에서 합계가 비어 보인다.
            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
            workbook.write(outputStream);
        }
    }

    private void setColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(COL_EMPLOYEE_CODE, 3500);
        sheet.setColumnWidth(COL_EMPLOYEE_NAME, 4000);
        sheet.setColumnWidth(COL_TEAM_NAME, 4000);
        sheet.setColumnWidth(COL_OVERTIME, 5000);
        sheet.setColumnWidth(COL_PAY, 6000);
    }

    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);

        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createDataRows(Sheet sheet, List<OverTimeReportData> reportData, CellStyle timeCellStyle, CellStyle currencyCellStyle) {

        int rowNum = 1;
        for (OverTimeReportData data : reportData) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(COL_EMPLOYEE_CODE).setCellValue(data.employeeCode());
            row.createCell(COL_EMPLOYEE_NAME).setCellValue(data.employeeName());
            row.createCell(COL_TEAM_NAME).setCellValue(data.teamName());

            // 분 단위를 엑셀 시간으로 변환
            // excel에서는 1이 하루(24시간)이다.
            // excelTime을 x라고 할 때,
            // 24 * 60 : date.overTimeMinutes() = 1 : x
            double excelTime = data.overTimeMinutes() / (24d * 60d);
            Cell timeCell = row.createCell(COL_OVERTIME);
            timeCell.setCellValue(excelTime);
            timeCell.setCellStyle(timeCellStyle);

            Cell payCell = row.createCell(COL_PAY);
            payCell.setCellValue(data.overTimePay());
            payCell.setCellStyle(currencyCellStyle);
        }
    }

    private void createTotalRow(Sheet sheet, int dataRowCount, CellStyle timeCellStyle, CellStyle currencyCellStyle) {
        int totalRowIdx = dataRowCount + 1;
        Row totalRow = sheet.createRow(totalRowIdx);

        Cell totalLabel = totalRow.createCell(COL_EMPLOYEE_CODE);
        totalLabel.setCellValue("합계");

        Cell totalTime = totalRow.createCell(COL_OVERTIME);
        totalTime.setCellStyle(timeCellStyle);

        Cell totalPay = totalRow.createCell(COL_PAY);
        totalPay.setCellStyle(currencyCellStyle);

        // 데이터 행이 없으면 SUM(D2:D1) 같은 역전 범위가 되어 헤더 행을 끌어들인다.
        if (dataRowCount == 0) {
            totalTime.setCellValue(0);
            totalPay.setCellValue(0);
            return;
        }

        // 데이터 행은 2행부터 시작한다 (1행은 헤더)
        int lastDataRow = dataRowCount + 1;
        totalTime.setCellFormula(sumFormula(COL_OVERTIME, lastDataRow));
        totalPay.setCellFormula(sumFormula(COL_PAY, lastDataRow));
    }

    private String sumFormula(int columnIndex, int lastDataRow) {
        String column = CellReference.convertNumToColString(columnIndex);
        return String.format("SUM(%s2:%s%d)", column, column, lastDataRow);
    }

    private CellStyle createTimeCellStyle(Workbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(dataFormat.getFormat("[h]:mm"));
        return style;
    }

    private CellStyle createCurrencyCellStyle(Workbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(dataFormat.getFormat("₩#,##0"));
        return style;
    }
}
