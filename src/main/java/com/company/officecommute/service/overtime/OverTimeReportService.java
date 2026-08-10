package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
public class OverTimeReportService {

    // TODO: 통상시급은 본래 직원별 속성. 현재 전 직원 동일값으로 단순화.
    // (가산 전 값. 가산율은 아래 MULTIPLIER 로 별도 적용)
    private static final long HOURLY_ORDINARY_WAGE = 15000;
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5"); // 연장근로·휴일근로 8h 이내 가산
    private static final BigDecimal HOLIDAY_EXCESS_MULTIPLIER = new BigDecimal("2.0"); // 휴일근로 8h 초과 가산

    // 조회 순서(DB 반환 순서)는 보장되지 않는다. 매달 같은 순서로 나와야 전월 리포트와 나란히 비교할 수 있다.
    // 사번은 unique 라 동점자가 남지 않는 전순서(total order)가 된다.
    private static final Comparator<OverTimeReportData> REPORT_ORDER =
            Comparator.comparing(OverTimeReportData::teamName)
                    .thenComparing(OverTimeReportData::employeeName)
                    .thenComparing(OverTimeReportData::employeeCode);

    private final OverTimeService overTimeService;
    private final OverTimeExcelWriter overTimeExcelWriter;

    public OverTimeReportService(
            OverTimeService overTimeService,
            OverTimeExcelWriter overTimeExcelWriter
    ) {
        this.overTimeService = overTimeService;
        this.overTimeExcelWriter = overTimeExcelWriter;
    }

    public void generateExcelReport(YearMonth yearMonth, OutputStream outputStream) throws IOException {
        writeExcelReport(generateReport(yearMonth), outputStream);
    }

    public void writeExcelReport(OverTimeReport report, OutputStream outputStream) throws IOException {
        overTimeExcelWriter.write(report, outputStream);
    }

    public OverTimeReport generateReport(YearMonth yearMonth) {
        List<OverTimeCalculateResponse> overTimeData = overTimeService.calculateOverTime(yearMonth);

        List<OverTimeReportData> rows = overTimeData.stream()
                .map(this::convertToReportData)
                .sorted(REPORT_ORDER)
                .toList();

        return new OverTimeReport(yearMonth, rows, overTimeService.countUnclosedCommutes(yearMonth));
    }

    private OverTimeReportData convertToReportData(OverTimeCalculateResponse response) {
        return new OverTimeReportData(
                response.employeeCode(),
                response.name(),
                response.teamName(),
                response.overTimeMinutes(),
                response.holidayWithin8HoursMinutes(),
                response.holidayExceeding8HoursMinutes(),
                calculateOverTimePay(response)
        );
    }

    // 반올림은 합산 후 한 번만 — 트랙별로 먼저 반올림하면 분 단위 비례가 어긋난다
    private long calculateOverTimePay(OverTimeCalculateResponse response) {
        BigDecimal multipliedMinutes = BigDecimal.valueOf(response.overTimeMinutes() + response.holidayWithin8HoursMinutes())
                .multiply(OVERTIME_MULTIPLIER)
                .add(BigDecimal.valueOf(response.holidayExceeding8HoursMinutes()).multiply(HOLIDAY_EXCESS_MULTIPLIER));
        return multipliedMinutes
                .multiply(BigDecimal.valueOf(HOURLY_ORDINARY_WAGE))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
