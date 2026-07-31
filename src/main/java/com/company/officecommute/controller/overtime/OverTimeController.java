package com.company.officecommute.controller.overtime;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.YearMonth;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
public class OverTimeController {

    private final OverTimeService overTimeService;
    private final OverTimeReportService overTimeReportService;

    public OverTimeController(
            OverTimeService overTimeService,
            OverTimeReportService overTimeReportService
    ) {
        this.overTimeService = overTimeService;
        this.overTimeReportService = overTimeReportService;
    }

    @ManagerOnly
    @GetMapping("/overtime")
    public List<OverTimeCalculateResponse> calculateOverTime(@RequestParam YearMonth yearMonth) {
        return overTimeService.calculateOverTime(yearMonth);
    }

    @ManagerOnly
    @GetMapping("/overtime/report/excel")
    public ResponseEntity<StreamingResponseBody> downloadOverTimeReport(@RequestParam YearMonth yearMonth) {
        // 스트리밍 시작 전에 집계를 끝낸다. 응답이 커밋된 뒤 실패하면 200 + 깨진 파일이 나간다.
        OverTimeReport report = overTimeReportService.generateReport(yearMonth);
        StreamingResponseBody body = outputStream ->
                overTimeReportService.writeExcelReport(report, outputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(yearMonth.getYear() + "년" + yearMonth.getMonthValue() + "월_초과근무보고서.xlsx", UTF_8).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }
}
