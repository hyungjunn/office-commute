package com.company.officecommute.controller.overtime;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;
import com.company.officecommute.service.overtime.OverTimeReportFileName;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.service.report.OverTimeReportDispatchService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.YearMonth;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api")
public class OverTimeController {

    private final OverTimeService overTimeService;
    private final OverTimeReportService overTimeReportService;
    private final OverTimeReportDispatchService overTimeReportDispatchService;

    public OverTimeController(
            OverTimeService overTimeService,
            OverTimeReportService overTimeReportService,
            OverTimeReportDispatchService overTimeReportDispatchService
    ) {
        this.overTimeService = overTimeService;
        this.overTimeReportService = overTimeReportService;
        this.overTimeReportDispatchService = overTimeReportDispatchService;
    }

    @ManagerOnly
    @GetMapping("/overtime")
    public List<OverTimeCalculateResponse> calculateOverTime(@RequestParam YearMonth yearMonth) {
        return overTimeService.calculateOverTime(yearMonth);
    }

    /**
     * 수동 재실행. 배치와 같은 멱등 경로라 이미 발송된 달에는 아무 일도 일어나지 않는다.
     * 관리자가 미마감을 고친 뒤 다음 재시도(최대 4시간 뒤)를 기다리지 않아도 되게 한다.
     */
    @ManagerOnly
    @PostMapping("/overtime/report/dispatch")
    public OverTimeReportDispatchResponse dispatchOverTimeReport(@RequestParam YearMonth yearMonth) {
        return overTimeReportDispatchService.dispatchAndDescribe(yearMonth);
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
        headers.setContentDisposition(ContentDisposition.attachment().filename(OverTimeReportFileName.of(yearMonth), UTF_8).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }
}
