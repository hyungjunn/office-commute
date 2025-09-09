package com.company.officecommute.controller.commute;

import com.company.officecommute.dto.commute.request.WorkDurationPerDateRequest;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.time.ZonedDateTime;

@RestController
public class CommuteHistoryController {

    private final CommuteHistoryService commuteHistoryService;

    public CommuteHistoryController(CommuteHistoryService commuteHistoryService) {
        this.commuteHistoryService = commuteHistoryService;
    }

    @PostMapping("/commute")
    public void registerWorkStartTime(@SessionAttribute("employeeId") Long employeeId) {
        commuteHistoryService.registerWorkStartTime(employeeId);
    }

    @PutMapping("/commute")
    public void registerWorkEndTime(@SessionAttribute("employeeId") Long employeeId) {
        commuteHistoryService.registerWorkEndTime(employeeId, ZonedDateTime.now());
    }

    @GetMapping("/commute")
    public WorkDurationPerDateResponse getWorkDurationPerDate(
            @SessionAttribute("employeeId") Long employeeId,
            WorkDurationPerDateRequest dateRequest) {
        return commuteHistoryService.getWorkDurationPerDate(employeeId, dateRequest.yearMonth());
    }
}
