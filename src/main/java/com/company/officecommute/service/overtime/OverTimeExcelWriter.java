package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeReport;
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

    // 신뢰성 알림은 헤더 위에 고정한다. 건수가 0이어도 행을 유지해야 달마다 레이아웃이 같고,
    // "확인했고 문제없음"과 "확인 자체를 안 함"이 구분된다.
    private static final int NOTICE_ROW = 0;
    private static final int HEADER_ROW = 1;
    private static final int FIRST_DATA_ROW = 2;

    // 행 수가 직원 수로 묶여 있어(수천 행) 전체를 메모리에 들고 가는 XSSF로 충분하다.
    // SXSSF와 달리 수식 계산이 가능해, 합계 수식에 계산된 값까지 함께 저장할 수 있다.
    public void write(OverTimeReport report, OutputStream outputStream) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName(report.yearMonth()));
            setColumnWidths(sheet);
            createNoticeRow(sheet, report);
            createHeader(sheet);

            CellStyle timeCellStyle = createTimeCellStyle(workbook);
            CellStyle currencyCellStyle = createCurrencyCellStyle(workbook);
            createDataRows(sheet, report.rows(), timeCellStyle, currencyCellStyle);
            createTotalRow(sheet, report.rows().size(), timeCellStyle, currencyCellStyle);

            // 수식만 저장하면 계산된 값이 없어, 열 때 재계산하지 않는 뷰어(메일 미리보기 등)에서 합계가 비어 보인다.
            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
            workbook.write(outputStream);
        }
    }

    // 월만 넣으면 2024-08과 2025-08 시트가 구분되지 않는다. 여러 달치를 모아 보관하는 쪽에서 헷갈린다.
    private String sheetName(YearMonth yearMonth) {
        return String.format("%d년 %d월 초과근무 보고서", yearMonth.getYear(), yearMonth.getMonthValue());
    }

    private void setColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(COL_EMPLOYEE_CODE, 3500);
        sheet.setColumnWidth(COL_EMPLOYEE_NAME, 4000);
        sheet.setColumnWidth(COL_TEAM_NAME, 4000);
        sheet.setColumnWidth(COL_OVERTIME, 5000);
        sheet.setColumnWidth(COL_PAY, 6000);
    }

    /**
     * 퇴근 미마감 기록은 {@code workingMinutes = 0}으로 합계에 들어가므로, 그 직원의 초과근무는
     * 실제보다 적게 나온다. 수치만 보면 "야근 안 함"과 구분되지 않으니 파일 안에 근거를 남긴다.
     */
    private void createNoticeRow(Sheet sheet, OverTimeReport report) {
        Cell notice = sheet.createRow(NOTICE_ROW).createCell(COL_EMPLOYEE_CODE);
        notice.setCellValue(noticeText(report));
        notice.setCellStyle(createNoticeStyle(sheet.getWorkbook(), report.hasUnclosedCommutes()));
    }

    private String noticeText(OverTimeReport report) {
        if (!report.hasUnclosedCommutes()) {
            return "퇴근 미마감 0건 — 대상 월의 출근 기록이 모두 마감되었습니다.";
        }
        return String.format(
                "[주의] 퇴근 미마감 %d건 — 해당 기록은 0분으로 집계되어, 아래 초과근무가 실제보다 적을 수 있습니다.",
                report.unclosedCommuteCount()
        );
    }

    private CellStyle createNoticeStyle(Workbook workbook, boolean hasUnclosedCommutes) {
        Font font = workbook.createFont();
        font.setBold(true);
        if (hasUnclosedCommutes) {
            font.setColor(IndexedColors.DARK_RED.getIndex());
        }
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(HEADER_ROW);

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

        int rowNum = FIRST_DATA_ROW;
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
        Row totalRow = sheet.createRow(FIRST_DATA_ROW + dataRowCount);

        Cell totalLabel = totalRow.createCell(COL_EMPLOYEE_CODE);
        totalLabel.setCellValue("합계");

        Cell totalTime = totalRow.createCell(COL_OVERTIME);
        totalTime.setCellStyle(timeCellStyle);

        Cell totalPay = totalRow.createCell(COL_PAY);
        totalPay.setCellStyle(currencyCellStyle);

        // 데이터 행이 없으면 SUM(D3:D2) 같은 역전 범위가 되어 헤더 행을 끌어들인다.
        if (dataRowCount == 0) {
            totalTime.setCellValue(0);
            totalPay.setCellValue(0);
            return;
        }

        // 엑셀 행 번호는 1부터 시작하므로 POI 인덱스 + 1
        int firstExcelRow = FIRST_DATA_ROW + 1;
        int lastExcelRow = FIRST_DATA_ROW + dataRowCount;
        totalTime.setCellFormula(sumFormula(COL_OVERTIME, firstExcelRow, lastExcelRow));
        totalPay.setCellFormula(sumFormula(COL_PAY, firstExcelRow, lastExcelRow));
    }

    private String sumFormula(int columnIndex, int firstExcelRow, int lastExcelRow) {
        String column = CellReference.convertNumToColString(columnIndex);
        return String.format("SUM(%s%d:%s%d)", column, firstExcelRow, column, lastExcelRow);
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
