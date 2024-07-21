package com.company.officecommute.controller.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.overtime.OverTimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
public class OverTimeController {

    private final OverTimeService overTimeService;

    public OverTimeController(OverTimeService overTimeService) {
        this.overTimeService = overTimeService;
    }

    @GetMapping("/overtime")
    public List<OverTimeCalculateResponse> calculateOverTime(@RequestParam YearMonth yearMonth) {
        return overTimeService.calculateOverTime(yearMonth);
    }

}
